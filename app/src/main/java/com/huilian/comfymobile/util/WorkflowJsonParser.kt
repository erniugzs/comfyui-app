package com.huilian.comfymobile.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.huilian.comfymobile.data.models.EditableNode
import com.huilian.comfymobile.data.models.NodeTypeInfo
import com.huilian.comfymobile.data.models.ParamOptions
import com.huilian.comfymobile.data.models.UiInput
import com.huilian.comfymobile.data.models.UiLink
import com.huilian.comfymobile.data.models.UiNode
import com.huilian.comfymobile.data.models.UiOutput
import com.huilian.comfymobile.data.models.UiWorkflow

class WorkflowJsonParser(private val nodeMetadataManager: NodeMetadataManager) {

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setLenient()
        .serializeSpecialFloatingPointValues()
        .create()
    private val nodeMetadataCache = mutableMapOf<String, NodeTypeInfo>()
    private val nodeParamTypesCache = mutableMapOf<String, Map<String, ParamType>>()

    companion object {
        private const val TAG = "WorkflowJsonParser"
        private val COSMETIC_NODE_TYPES = setOf(
            "MarkdownNote", "Note", "NotePrimitive", "StickyNote", "Reroute"
        )
        private val VALUE_PROVIDER_TYPES = setOf("PrimitiveNode")
        private val UI_ONLY_WIDGETS = setOf("control_after_generate")
        private val CONTROL_AFTER_GENERATE_VALUES = setOf(
            "fixed", "randomize", "increment", "decrement"
        )
    }

    enum class ParamType {
        INT, FLOAT, STRING, BOOLEAN, ENUM, CONNECTION
    }

    // ==================== Public API ====================

    suspend fun parseUiWorkflow(jsonString: String): UiWorkflow {
        val rootElement = gson.fromJson(jsonString, JsonElement::class.java)
            ?: throw IllegalArgumentException("Empty or invalid JSON")
        if (!rootElement.isJsonObject) {
            throw IllegalArgumentException("Top-level JSON is not an object")
        }
        val rootObj = rootElement.asJsonObject

        // Check for prompt + extra_data format (/api/prompt response)
        if (rootObj.has("prompt") && rootObj.has("extra_data")) {
            val extraData = rootObj.get("extra_data")?.takeIf { it.isJsonObject }?.asJsonObject
            val extraPnginfo = extraData?.get("extra_pnginfo")?.takeIf { it.isJsonObject }?.asJsonObject
            val workflowObj = extraPnginfo?.get("workflow")?.takeIf { it.isJsonObject }?.asJsonObject
            if (workflowObj != null) {
                return parseUiWorkflowFromObject(workflowObj)
            }
        }

        return parseUiWorkflowFromObject(rootObj)
    }

    fun serializeWorkflowToJson(workflow: UiWorkflow): String {
        return gson.toJson(serializeWorkflowToJsonObject(workflow))
    }

    suspend fun buildApiWorkflow(uiWorkflow: UiWorkflow): JsonObject {
        val expanded = expandSubgraphs(uiWorkflow)
        val promptObj = buildApiPromptObject(expanded)
        val result = JsonObject()
        result.add("prompt", promptObj)
        val extraData = JsonObject()
        val extraPnginfo = JsonObject()
        extraPnginfo.add("workflow", serializeWorkflowToJsonObject(expanded))
        extraData.add("extra_pnginfo", extraPnginfo)
        result.add("extra_data", extraData)
        return result
    }

    suspend fun expandSubgraphs(uiWorkflow: UiWorkflow, maxDepth: Int = 10): UiWorkflow {
        if (maxDepth <= 0) return uiWorkflow
        val definitions = uiWorkflow.definitions ?: return uiWorkflow
        val subgraphsArr = definitions.get("subgraphs")?.takeIf { it.isJsonArray }?.asJsonArray ?: return uiWorkflow

        val subgraphMap = mutableMapOf<String, JsonObject>()
        for (subElem in subgraphsArr) {
            if (!subElem.isJsonObject) continue
            val subObj = subElem.asJsonObject
            val id = subObj.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            subgraphMap[id] = subObj
        }
        if (subgraphMap.isEmpty()) return uiWorkflow

        val subgraphNodes = uiWorkflow.nodes.filter { subgraphMap.containsKey(it.type) }
        if (subgraphNodes.isEmpty()) return uiWorkflow

        Log.d(TAG, "Expanding ${subgraphNodes.size} subgraph node(s)")

        val newNodes = mutableListOf<UiNode>()
        val newLinks = mutableListOf<UiLink>()
        var nextLinkId = ((uiWorkflow.links.maxOfOrNull { it.id } ?: 0) + 10000)
        val idMapping = mutableMapOf<String, String>()
        val subgraphNodeIds = subgraphNodes.map { sanitizeId(it.id) }.toSet()

        // Build output remappings
        val outputRemappings = mutableMapOf<Pair<String, Int>, Pair<String, Int>>()
        for (subNode in subgraphNodes) {
            val def = subgraphMap[subNode.type] ?: continue
            val sanitizeId = sanitizeId(subNode.id)
            val subLinks = parseLinksArray(def.get("links")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray())
            for (link in subLinks) {
                if (link.target_id == "-20") {
                    val key = Pair(sanitizeId, link.target_slot)
                    val value = Pair(sanitizeId + ":" + sanitizeId(link.origin_id), link.origin_slot)
                    if (!outputRemappings.containsKey(key)) {
                        outputRemappings[key] = value
                    }
                }
            }
        }
        Log.d(TAG, "Output remappings built: $outputRemappings")

        // Build target links map
        val targetLinksMap = mutableMapOf<String, MutableList<UiLink>>()
        for (link in uiWorkflow.links) {
            targetLinksMap.getOrPut(sanitizeId(link.target_id)) { mutableListOf() }.add(link)
        }

        // Expand each node
        for (node in uiWorkflow.nodes) {
            val def = subgraphMap[node.type]
            if (def == null) {
                newNodes.add(node)
                continue
            }

            val sanitizeId = sanitizeId(node.id)
            val subNodesArr = def.get("nodes")?.takeIf { it.isJsonArray }?.asJsonArray
            val subLinksArr = def.get("links")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            if (subNodesArr == null) {
                newNodes.add(node)
                continue
            }

            // Build input remappings from external links
            val inputRemap = mutableMapOf<String, Pair<String, Int>>()
            val incoming = targetLinksMap[sanitizeId] ?: emptyList()
            for (link in incoming) {
                val uiInput = node.inputs.getOrNull(link.target_slot)
                if (uiInput != null) {
                    inputRemap[uiInput.name] = Pair(link.origin_id, link.origin_slot)
                }
            }

            // Parse proxyWidgets
            val proxyWidgets = node.properties?.get("proxyWidgets")?.takeIf { it.isJsonArray }?.asJsonArray
            val directProxyValues = mutableMapOf<String, JsonElement>()
            val subProxyValues = mutableMapOf<Pair<String, String>, JsonElement>()
            val widgetsValues = node.widgets_values
            if (proxyWidgets != null && widgetsValues != null) {
                for (i in 0 until proxyWidgets.size()) {
                    val pwElem = proxyWidgets.get(i)
                    if (!pwElem.isJsonArray || pwElem.asJsonArray.size() < 2) continue
                    val pwArr = pwElem.asJsonArray
                    val targetId = sanitizeId(pwArr.get(0).asString)
                    val label = pwArr.get(1).asString
                    if (i < widgetsValues.size()) {
                        val value = widgetsValues[i]
                        if (!value.isJsonNull) {
                            if (targetId == "-1") {
                                directProxyValues[label] = value
                            } else {
                                subProxyValues[Pair(targetId, label)] = value
                            }
                        }
                    }
                }
            }

            // Build sub-input name map
            val subInputsNameMap = mutableMapOf<Int, String>()
            val defInputsArr = def.get("inputs")?.takeIf { it.isJsonArray }?.asJsonArray
            if (defInputsArr != null) {
                for (i in 0 until defInputsArr.size()) {
                    val inpElem = defInputsArr.get(i)
                    if (!inpElem.isJsonObject) continue
                    val name = inpElem.asJsonObject.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                    if (name != null) {
                        subInputsNameMap[i] = name
                    }
                }
            }

            // Build internal link map by target
            val subLinkByTarget = mutableMapOf<Pair<String, Int>, UiLink>()
            val parsedSubLinks = parseLinksArray(subLinksArr)
            for (link in parsedSubLinks) {
                if (link.target_id != "-20") {
                    subLinkByTarget[Pair(link.target_id, link.target_slot)] = link
                }
            }

            // Create sub-node ID mapping
            val subIdMapping = mutableMapOf<String, String>()
            val subNodeObjects = mutableListOf<JsonObject>()
            for (subNodeElem in subNodesArr) {
                if (!subNodeElem.isJsonObject) continue
                val subNodeObj = subNodeElem.asJsonObject
                val oldId = subNodeObj.get("id")?.let { if (it.isJsonPrimitive) it.asString else it.toString() } ?: continue
                val newId = (newNodes.size + subNodeObjects.size).toString()
                subIdMapping[oldId] = newId
                idMapping["$sanitizeId:$oldId"] = newId
                subNodeObjects.add(subNodeObj)
            }

            // Process each sub-node
            val expandedSubNodes = mutableListOf<UiNode>()
            for (subNodeObj in subNodeObjects) {
                val oldId = subNodeObj.get("id")?.let { if (it.isJsonPrimitive) it.asString else it.toString() } ?: continue
                val newId = subIdMapping[oldId] ?: continue
                val subType = subNodeObj.get("type")?.asString ?: ""
                val subInputsArrLocal = subNodeObj.get("inputs")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
                val subOutputsArrLocal = subNodeObj.get("outputs")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
                val subWidgets = subNodeObj.get("widgets_values")?.takeIf { it.isJsonArray }?.asJsonArray
                val subPos = subNodeObj.get("pos")?.takeIf { it.isJsonArray }?.asJsonArray
                val subSize = subNodeObj.get("size")?.takeIf { it.isJsonArray }?.asJsonArray
                val subFlags = subNodeObj.get("flags")?.takeIf { it.isJsonObject }?.asJsonObject
                val subOrder = subNodeObj.get("order")?.asInt
                val subMode = subNodeObj.get("mode")?.asInt
                val subProperties = subNodeObj.get("properties")?.takeIf { it.isJsonObject }?.asJsonObject
                val subColor = subNodeObj.get("color")?.asString
                val subBgcolor = subNodeObj.get("bgcolor")?.asString

                val parsedInputs = parseInputsArray(subInputsArrLocal).toMutableList()
                val parsedOutputs = parseOutputsArray(subOutputsArrLocal)

                // Remap input links for cross-subgraph connections
                val remappedInputs = parsedInputs.mapIndexed { idx, inp ->
                    val linkVal = inp.link
                    if (linkVal != null && linkVal.isJsonPrimitive && linkVal.asJsonPrimitive.isNumber) {
                        val linkId = linkVal.asInt
                        val subLink = subLinkByTarget[Pair(oldId, idx)]
                        if (subLink != null) {
                            if (subLink.origin_id == "-10") {
                                val extLabel = subInputsNameMap[subLink.target_slot]
                                if (extLabel != null) {
                                    val extPair = inputRemap[extLabel]
                                    if (extPair != null) {
                                        val crossKey = Pair(extPair.first, extPair.second)
                                        val crossRemap = outputRemappings[crossKey]
                                        if (crossRemap != null) {
                                            Log.d(TAG, "Cross-subgraph remap: ${extPair.first}:${extPair.second} -> ${crossRemap.first}:${crossRemap.second}")
                                            val newLinkId = nextLinkId++
                                            newLinks.add(UiLink(newLinkId, crossRemap.first, crossRemap.second, newId, idx, subLink.type))
                                            inp.copy(link = JsonPrimitive(newLinkId))
                                        } else {
                                            Log.w(TAG, "No output remapping for cross-subgraph ref ${extPair.first}:${extPair.second}")
                                            inp.copy(link = JsonNull.INSTANCE)
                                        }
                                    } else {
                                        inp.copy(link = JsonNull.INSTANCE)
                                    }
                                } else {
                                    inp.copy(link = JsonNull.INSTANCE)
                                }
                            } else {
                                val mappedOrigin = subIdMapping[subLink.origin_id]
                                if (mappedOrigin != null) {
                                    val newLinkId = nextLinkId++
                                    newLinks.add(UiLink(newLinkId, mappedOrigin, subLink.origin_slot, newId, idx, subLink.type))
                                    inp.copy(link = JsonPrimitive(newLinkId))
                                } else {
                                    inp.copy(link = JsonNull.INSTANCE)
                                }
                            }
                        } else {
                            inp
                        }
                    } else {
                        inp
                    }
                }

                // Apply proxy widget values
                val finalWidgets = subWidgets?.deepCopy() ?: JsonArray()
                for ((pIdx, pValue) in directProxyValues) {
                    val targetIdx = pIdx.toIntOrNull()
                    if (targetIdx != null && targetIdx >= 0) {
                        if (targetIdx < finalWidgets.size()) {
                            finalWidgets.set(targetIdx, pValue)
                        } else {
                            while (finalWidgets.size() < targetIdx) {
                                finalWidgets.add(JsonNull.INSTANCE)
                            }
                            finalWidgets.add(pValue)
                        }
                    }
                }
                val subProxy = subProxyValues[Pair(newId, "")]
                // (Sub-proxy logic is complex; simplified here)

                // Build widget labels from metadata
                val widgetLabels = try {
                    loadNodeMetadata(subType)
                    extractWidgetLabels(nodeMetadataCache[subType])
                } catch (e: Exception) {
                    emptyMap()
                }

                expandedSubNodes.add(
                    UiNode(
                        id = newId,
                        type = subType,
                        inputs = remappedInputs,
                        outputs = parsedOutputs,
                        widgets_values = if (finalWidgets.size() > 0) finalWidgets else null,
                        widget_labels = widgetLabels.ifEmpty { null },
                        pos = subPos,
                        size = subSize,
                        flags = subFlags,
                        order = subOrder,
                        mode = subMode,
                        properties = subProperties,
                        color = subColor,
                        bgcolor = subBgcolor
                    )
                )
            }
            newNodes.addAll(expandedSubNodes)
        }

        // Process external links
        for (link in uiWorkflow.links) {
            val originId = sanitizeId(link.origin_id)
            val targetId = sanitizeId(link.target_id)
            if (subgraphNodeIds.contains(targetId)) {
                // Dropped: links into subgraph nodes are replaced by internal remappings
                continue
            }
            if (subgraphNodeIds.contains(originId)) {
                val remap = outputRemappings[Pair(originId, link.origin_slot)]
                if (remap != null) {
                    newLinks.add(
                        UiLink(
                            id = link.id,
                            origin_id = remap.first,
                            origin_slot = remap.second,
                            target_id = targetId,
                            target_slot = link.target_slot,
                            type = link.type
                        )
                    )
                } else {
                    Log.w(TAG, "No output remapping for $originId slot ${link.origin_slot}")
                }
            } else {
                newLinks.add(link)
            }
        }

        Log.d(TAG, "Subgraph expansion: ${uiWorkflow.nodes.size} nodes -> ${newNodes.size} nodes, ${uiWorkflow.links.size} links -> ${newLinks.size} links")
        val expanded = uiWorkflow.copy(
            nodes = newNodes,
            links = newLinks
        )
        return expandSubgraphs(expanded, maxDepth - 1)
    }

    // Backward compatibility helpers
    suspend fun parse(jsonString: String): UiWorkflow = parseUiWorkflow(jsonString)

    fun toEditableNodes(workflow: UiWorkflow): List<EditableNode> {
        return workflow.nodes.map { EditableNode.fromUiNode(it) }
    }

    fun serialize(workflow: UiWorkflow): String = serializeWorkflowToJson(workflow)

    suspend fun toApiJson(uiWorkflow: UiWorkflow, editableNodes: List<EditableNode>? = null): JsonObject {
        val updatedWorkflow = if (editableNodes != null) {
            applyEditableNodes(uiWorkflow, editableNodes)
        } else uiWorkflow
        return buildApiWorkflow(updatedWorkflow)
    }

    private fun applyEditableNodes(uiWorkflow: UiWorkflow, editableNodes: List<EditableNode>): UiWorkflow {
        val editableMap = editableNodes.associateBy { it.id }
        val updatedNodes = uiWorkflow.nodes.map { node ->
            val editable = editableMap[node.id]
            if (editable != null && node.widgets_values != null) {
                val newWidgetsValues = JsonArray()
                val widgetLabels = node.widget_labels ?: emptyMap()
                for (i in 0 until node.widgets_values.size()) {
                    val originalValue = node.widgets_values[i]
                    val label = widgetLabels[i] ?: "Widget #$i"
                    val editedWidget = editable.widgets.find { it.label == label }
                    if (editedWidget != null) {
                        val editedStr = editedWidget.value
                        when {
                            originalValue.isJsonPrimitive && originalValue.asJsonPrimitive.isNumber -> {
                                val num = editedStr.toDoubleOrNull()
                                if (num != null) {
                                    if (num == num.toInt().toDouble()) {
                                        newWidgetsValues.add(JsonPrimitive(num.toInt()))
                                    } else {
                                        newWidgetsValues.add(JsonPrimitive(num))
                                    }
                                } else {
                                    newWidgetsValues.add(JsonPrimitive(editedStr))
                                }
                            }
                            originalValue.isJsonPrimitive && originalValue.asJsonPrimitive.isBoolean -> {
                                newWidgetsValues.add(JsonPrimitive(editedStr.equals("true", ignoreCase = true)))
                            }
                            else -> newWidgetsValues.add(JsonPrimitive(editedStr))
                        }
                    } else {
                        newWidgetsValues.add(originalValue)
                    }
                }
                node.copy(widgets_values = newWidgetsValues)
            } else node
        }
        return uiWorkflow.copy(nodes = updatedNodes)
    }

    // ==================== Parsing ====================

    private suspend fun parseUiWorkflowFromObject(rootObj: JsonObject): UiWorkflow {
        val version = rootObj.get("version")?.asInt ?: 1
        val nodesArr = rootObj.get("nodes")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw IllegalArgumentException("Missing 'nodes' in JSON")
        val linksArr = rootObj.get("links")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val groups = rootObj.get("groups")?.takeIf { it.isJsonArray }?.asJsonArray
        val config = rootObj.get("config")?.takeIf { it.isJsonObject }?.asJsonObject
        val extra = rootObj.get("extra")?.takeIf { it.isJsonObject }?.asJsonObject
        val definitions = rootObj.get("definitions")?.takeIf { it.isJsonObject }?.asJsonObject
        val lastNodeId = rootObj.get("last_node_id")?.asInt
        val lastLinkId = rootObj.get("last_link_id")?.asInt

        val nodes = parseNodesArray(nodesArr)
        val links = parseLinksArray(linksArr)

        // Load metadata for subgraph node types
        definitions?.get("subgraphs")?.takeIf { it.isJsonArray }?.asJsonArray?.let { subgraphsArr ->
            val types = mutableSetOf<String>()
            for (subElem in subgraphsArr) {
                if (!subElem.isJsonObject) continue
                val nodesInSub = subElem.asJsonObject.get("nodes")?.takeIf { it.isJsonArray }?.asJsonArray
                nodesInSub?.forEach { nodeElem ->
                    if (nodeElem.isJsonObject) {
                        val type = nodeElem.asJsonObject.get("type")?.asString
                        if (type != null) types.add(type)
                    }
                }
            }
            for (type in types) {
                try {
                    loadNodeMetadata(type)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load metadata for subgraph type $type", e)
                }
            }
        }

        return UiWorkflow(
            version = version,
            nodes = nodes,
            links = links,
            groups = groups,
            config = config,
            extra = extra,
            definitions = definitions,
            lastNodeId = lastNodeId,
            lastLinkId = lastLinkId
        )
    }

    private suspend fun parseNodesArray(nodesArr: JsonArray): List<UiNode> {
        val result = mutableListOf<UiNode>()
        for (nodeElem in nodesArr) {
            if (!nodeElem.isJsonObject) {
                Log.e(TAG, "Skipping non-object node element: $nodeElem")
                continue
            }
            val nodeObj = nodeElem.asJsonObject

            val id = nodeObj.get("id")?.let {
                if (it.isJsonPrimitive) it.asString else it.toString()
            } ?: "UnknownId"
            val type = nodeObj.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: "UnknownType"
            val inputsArr = nodeObj.get("inputs")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            val outputsArr = nodeObj.get("outputs")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            val widgetsValues = nodeObj.get("widgets_values")?.takeIf { it.isJsonArray }?.asJsonArray
            val pos = nodeObj.get("pos")?.takeIf { it.isJsonArray }?.asJsonArray
            val size = nodeObj.get("size")?.takeIf { it.isJsonArray }?.asJsonArray
            val flags = nodeObj.get("flags")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            val order = nodeObj.get("order")?.takeIf { it.isJsonPrimitive }?.asInt
            val mode = nodeObj.get("mode")?.takeIf { it.isJsonPrimitive }?.asInt
            val properties = nodeObj.get("properties")?.takeIf { it.isJsonObject }?.asJsonObject
            val color = nodeObj.get("color")?.takeIf { it.isJsonPrimitive }?.asString
            val bgcolor = nodeObj.get("bgcolor")?.takeIf { it.isJsonPrimitive }?.asString

            val inputs = parseInputsArray(inputsArr)
            val outputs = parseOutputsArray(outputsArr)

            // Load metadata and extract widget labels ourselves (do not rely on getWidgetLabelsMap)
            val widgetLabels = if (type.isNotEmpty()) {
                try {
                    loadNodeMetadata(type)
                    extractWidgetLabels(nodeMetadataCache[type])
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load metadata for $type, continuing with empty labels", e)
                    emptyMap()
                }
            } else emptyMap()

            // Fallback: build labels from proxyWidgets in properties
            val finalLabels = if (widgetLabels.isEmpty() && properties != null) {
                val proxyWidgets = properties.get("proxyWidgets")?.takeIf { it.isJsonArray }?.asJsonArray
                if (proxyWidgets != null) {
                    val proxyMap = mutableMapOf<Int, String>()
                    for (i in 0 until proxyWidgets.size()) {
                        val pwElem = proxyWidgets.get(i)
                        if (pwElem.isJsonArray && pwElem.asJsonArray.size() >= 2) {
                            val label = pwElem.asJsonArray.get(1).let {
                                if (it.isJsonPrimitive) it.asString else it.toString()
                            }
                            proxyMap[i] = label
                        }
                    }
                    if (proxyMap.isNotEmpty()) {
                        Log.d(TAG, "parseNodesArray: built proxyWidget labels for $type node $id: $proxyMap")
                    }
                    proxyMap
                } else emptyMap()
            } else widgetLabels

            result.add(
                UiNode(
                    id = id,
                    type = type,
                    inputs = inputs,
                    outputs = outputs,
                    widgets_values = widgetsValues,
                    widget_labels = finalLabels.ifEmpty { null },
                    pos = pos,
                    size = size,
                    flags = flags,
                    order = order,
                    mode = mode,
                    properties = properties,
                    color = color,
                    bgcolor = bgcolor
                )
            )
        }
        return result
    }

    private fun parseInputsArray(inputsArr: JsonArray): List<UiInput> {
        val result = mutableListOf<UiInput>()
        for (inputElem in inputsArr) {
            if (!inputElem.isJsonObject) continue
            val inputObj = inputElem.asJsonObject
            val name = inputObj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "unknown"
            val type = inputObj.get("type") ?: JsonNull.INSTANCE
            val link = inputObj.get("link")
            val slotIndex = inputObj.get("slot_index")
            val localizedName = inputObj.get("localized_name")?.takeIf { it.isJsonPrimitive }?.asString
            val shape = inputObj.get("shape")?.takeIf { it.isJsonPrimitive }?.asInt
            val widget = inputObj.get("widget")
            result.add(
                UiInput(
                    name = name,
                    type = type,
                    link = link,
                    slot_index = slotIndex,
                    localizedName = localizedName,
                    shape = shape,
                    widget = widget
                )
            )
        }
        return result
    }

    private fun parseOutputsArray(outputsArr: JsonArray): List<UiOutput> {
        val result = mutableListOf<UiOutput>()
        for (outputElem in outputsArr) {
            if (!outputElem.isJsonObject) continue
            val outputObj = outputElem.asJsonObject
            val name = outputObj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "unknownOutput"
            val type = outputObj.get("type") ?: JsonNull.INSTANCE
            val linksArr = outputObj.get("links")?.takeIf { it.isJsonArray }?.asJsonArray
            val slotIndex = outputObj.get("slot_index")
            val localizedName = outputObj.get("localized_name")?.takeIf { it.isJsonPrimitive }?.asString
            val shape = outputObj.get("shape")?.takeIf { it.isJsonPrimitive }?.asInt
            val widget = outputObj.get("widget")
            val links = linksArr?.mapNotNull {
                if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asInt else null
            }
            result.add(
                UiOutput(
                    name = name,
                    type = type,
                    links = links,
                    slot_index = slotIndex,
                    localizedName = localizedName,
                    shape = shape,
                    widget = widget
                )
            )
        }
        return result
    }

    private fun parseLinksArray(linksArr: JsonArray): List<UiLink> {
        val result = mutableListOf<UiLink>()
        for (linkElem in linksArr) {
            when {
                linkElem.isJsonArray -> {
                    val linkArray = linkElem.asJsonArray
                    if (linkArray.size() >= 5) {
                        val idElem = linkArray.get(0)
                        val id = if (idElem.isJsonPrimitive && idElem.asJsonPrimitive.isNumber) idElem.asInt else 0
                        val originId = linkArray.get(1).let {
                            if (it.isJsonPrimitive) it.asString else it.toString()
                        }
                        val originSlotElem = linkArray.get(2)
                        val originSlot = if (originSlotElem.isJsonPrimitive && originSlotElem.asJsonPrimitive.isNumber) originSlotElem.asInt else 0
                        val targetId = linkArray.get(3).let {
                            if (it.isJsonPrimitive) it.asString else it.toString()
                        }
                        val targetSlotElem = linkArray.get(4)
                        val targetSlot = if (targetSlotElem.isJsonPrimitive && targetSlotElem.asJsonPrimitive.isNumber) targetSlotElem.asInt else 0
                        val type = if (linkArray.size() > 5) {
                            linkArray.get(5).let { if (it.isJsonPrimitive) it.asString else it.toString() }
                        } else null
                        result.add(
                            UiLink(
                                id = id,
                                origin_id = sanitizeId(originId),
                                origin_slot = originSlot,
                                target_id = sanitizeId(targetId),
                                target_slot = targetSlot,
                                type = type
                            )
                        )
                    }
                }
                linkElem.isJsonObject -> {
                    val linkObj = linkElem.asJsonObject
                    val id = linkObj.get("id")?.asInt ?: 0
                    val originId = linkObj.get("origin_id")?.let {
                        if (it.isJsonPrimitive) it.asString else it.toString()
                    } ?: "unknownOrigin"
                    val originSlot = linkObj.get("origin_slot")?.asInt ?: 0
                    val targetId = linkObj.get("target_id")?.let {
                        if (it.isJsonPrimitive) it.asString else it.toString()
                    } ?: "unknownTarget"
                    val targetSlot = linkObj.get("target_slot")?.asInt ?: 0
                    val type = linkObj.get("type")?.let {
                        if (it.isJsonPrimitive) it.asString else it.toString()
                    }
                    result.add(
                        UiLink(
                            id = id,
                            origin_id = sanitizeId(originId),
                            origin_slot = originSlot,
                            target_id = sanitizeId(targetId),
                            target_slot = targetSlot,
                            type = type
                        )
                    )
                }
                else -> {
                    Log.e(TAG, "Unsupported link format: $linkElem")
                }
            }
        }
        return result
    }

    // ==================== Serialization ====================

    private fun serializeWorkflowToJsonObject(workflow: UiWorkflow): JsonObject {
        val jsonObject = JsonObject()
        jsonObject.addProperty("version", workflow.version)
        workflow.lastNodeId?.let { jsonObject.addProperty("last_node_id", it) }
        workflow.lastLinkId?.let { jsonObject.addProperty("last_link_id", it) }

        val nodesArray = JsonArray()
        for (node in workflow.nodes) {
            val nodeObj = JsonObject()
            nodeObj.addProperty("id", sanitizeId(node.id))
            nodeObj.addProperty("type", node.type)
            node.pos?.let { nodeObj.add("pos", it) }
            node.size?.let { nodeObj.add("size", it) }
            node.flags?.let { nodeObj.add("flags", it) }
            node.order?.let { nodeObj.addProperty("order", it) }
            node.mode?.let { nodeObj.addProperty("mode", it) }
            node.color?.let { nodeObj.addProperty("color", it) }
            node.bgcolor?.let { nodeObj.addProperty("bgcolor", it) }
            node.properties?.let { nodeObj.add("properties", it) }

            val inputsArray = JsonArray()
            for (input in node.inputs) {
                val inputObj = JsonObject()
                inputObj.addProperty("name", input.name)
                input.localizedName?.let { inputObj.addProperty("localized_name", it) }
                input.type.let { inputObj.add("type", it) }
                input.link?.let { inputObj.add("link", it) }
                input.slot_index?.let { inputObj.add("slot_index", it) }
                input.shape?.let { inputObj.addProperty("shape", it) }
                input.widget?.let { inputObj.add("widget", it) }
                inputsArray.add(inputObj)
            }
            nodeObj.add("inputs", inputsArray)

            val outputsArray = JsonArray()
            for (output in node.outputs) {
                val outputObj = JsonObject()
                outputObj.addProperty("name", output.name)
                output.localizedName?.let { outputObj.addProperty("localized_name", it) }
                output.type.let { outputObj.add("type", it) }
                output.slot_index?.let { outputObj.add("slot_index", it) }
                output.shape?.let { outputObj.addProperty("shape", it) }
                val linksArr = JsonArray()
                output.links?.forEach { linksArr.add(it) }
                outputObj.add("links", linksArr)
                outputsArray.add(outputObj)
            }
            nodeObj.add("outputs", outputsArray)

            node.widgets_values?.let { nodeObj.add("widgets_values", it) }
            nodesArray.add(nodeObj)
        }
        jsonObject.add("nodes", nodesArray)

        val linksArray = JsonArray()
        for (link in workflow.links) {
            val linkArr = JsonArray()
            linkArr.add(link.id)
            try {
                linkArr.add(Integer.parseInt(sanitizeId(link.origin_id)))
            } catch (_: Exception) {
                linkArr.add(sanitizeId(link.origin_id))
            }
            linkArr.add(link.origin_slot)
            try {
                linkArr.add(Integer.parseInt(sanitizeId(link.target_id)))
            } catch (_: Exception) {
                linkArr.add(sanitizeId(link.target_id))
            }
            linkArr.add(link.target_slot)
            link.type?.let { linkArr.add(it) }
            linksArray.add(linkArr)
        }
        jsonObject.add("links", linksArray)

        jsonObject.add("groups", workflow.groups ?: JsonArray())
        workflow.definitions?.let { jsonObject.add("definitions", it) }
        jsonObject.add("config", workflow.config ?: JsonObject())
        workflow.extra?.let { jsonObject.add("extra", it) }

        val seedWidgets = JsonObject()
        seedWidgets.addProperty("3D", 0)
        jsonObject.add("seed_widgets", seedWidgets)

        return jsonObject
    }

    // ==================== API Prompt Building ====================

    private suspend fun buildApiPromptObject(uiWorkflow: UiWorkflow): JsonObject {
        val mutedNodes = uiWorkflow.nodes.filter { it.mode == 4 }.map { it.id }.toSet()
        val linkMap = uiWorkflow.links.associateBy { it.id }
        val nodeMap = uiWorkflow.nodes.associateBy { it.id }

        // Build input slot maps
        val nodeInputSlots = mutableMapOf<String, Map<Int, Int>>()
        for (node in uiWorkflow.nodes) {
            val slotMap = mutableMapOf<Int, Int>()
            for ((idx, input) in node.inputs.withIndex()) {
                val link = input.link
                val linkId = if (link != null && link.isJsonPrimitive && link.asJsonPrimitive.isNumber) {
                    link.asInt
                } else -1
                slotMap[idx] = linkId
            }
            nodeInputSlots[node.id] = slotMap
        }

        val rerouteNodeIds = uiWorkflow.nodes.filter { it.type == "Reroute" }.map { it.id }.toSet()
        val valueProviderNodeIds = uiWorkflow.nodes.filter { it.type in VALUE_PROVIDER_TYPES }.map { it.id }.toSet()

        // Find all nodes that are targets of links (connected nodes)
        val connectedTargetIds = mutableSetOf<String>()
        for (node in uiWorkflow.nodes) {
            for (input in node.inputs) {
                val link = input.link
                if (link != null && link.isJsonPrimitive && link.asJsonPrimitive.isNumber) {
                    val linkId = link.asInt
                    val uiLink = linkMap[linkId]
                    if (uiLink != null) {
                        connectedTargetIds.add(uiLink.origin_id)
                    }
                }
            }
        }

        val result = JsonObject()

        for (node in uiWorkflow.nodes) {
            if (node.id in mutedNodes) continue
            if (node.type in COSMETIC_NODE_TYPES) continue
            if (node.type in VALUE_PROVIDER_TYPES) continue

            val nodeObj = JsonObject()
            nodeObj.addProperty("class_type", node.type)

            val inputsObj = JsonObject()
            val widgetsArr = node.widgets_values ?: JsonArray()
            val widgetLabels = node.widget_labels ?: emptyMap()

            when (node.type) {
                "PrimitiveNode" -> processPrimitiveNode(inputsObj, widgetsArr)
                "KSampler" -> processKSamplerNode(inputsObj, widgetsArr)
                "LoadImage" -> processLoadImageNode(inputsObj, widgetsArr)
                "EmptySD3LatentImage" -> processEmptySD3LatentImage(inputsObj, widgetsArr)
                "UnetLoaderGGUF" -> processUnetLoaderGGUF(inputsObj, widgetsArr)
                "CLIPLoaderGGUF" -> processCLIPLoaderGGUF(inputsObj, widgetsArr)
                "VAELoader" -> processVAELoader(inputsObj, widgetsArr)
                "ModelSamplingAuraFlow" -> processModelSamplingAuraFlow(inputsObj, widgetsArr)
                "SamplerCustom" -> processSamplerCustomNode(inputsObj, widgetsArr)
                else -> {
                    loadNodeMetadata(node.type)
                    val meta = nodeMetadataCache[node.type]
                    val hasMeta = meta != null
                    val inputOrderList = meta?.input_order?.required
                    val inputRequiredKeys = meta?.input?.required?.keys
                    Log.d(
                        TAG,
                        "buildApi node ${node.id} type=${node.type}: cached=$hasMeta, inputOrder=$inputOrderList, inputRequired=$inputRequiredKeys, widgetsArr=${widgetsArr.size()}, widgetLabels=$widgetLabels"
                    )
                    if (meta != null) {
                        if (!processStandardNode(inputsObj, widgetsArr, widgetLabels, node.type, node.inputs)) {
                            Log.d(TAG, "processStandardNode FAILED for ${node.type}, trying smart generic")
                            if (!processSmartGenericNode(inputsObj, widgetsArr, node.inputs)) {
                                Log.d(TAG, "processSmartGenericNode FAILED for ${node.type}, using raw label fallback")
                                processRawLabelFallback(inputsObj, widgetsArr, widgetLabels)
                            }
                        }
                    } else {
                        if (!processSmartGenericNode(inputsObj, widgetsArr, node.inputs)) {
                            processRawLabelFallback(inputsObj, widgetsArr, widgetLabels)
                        }
                    }
                }
            }

            // Process linked inputs
            for ((idx, input) in node.inputs.withIndex()) {
                val link = input.link
                if (link != null && link.isJsonPrimitive && link.asJsonPrimitive.isNumber) {
                    val linkId = link.asInt
                    val uiLink = linkMap[linkId]
                    if (uiLink != null) {
                        val inputTypeStr = if (input.type.isJsonPrimitive) input.type.asString else input.type.toString()
                        val source = findUltimateSource(
                            uiLink.origin_id, uiLink.origin_slot,
                            inputTypeStr,
                            mutedNodes, nodeInputSlots, linkMap, uiWorkflow, rerouteNodeIds
                        )
                        if (source != null) {
                            val (originId, originSlot) = source
                            if (originId in valueProviderNodeIds) {
                                val providerNode = nodeMap[originId]
                                val providerWidgets = providerNode?.widgets_values
                                if (providerWidgets != null && providerWidgets.size() > 0) {
                                    inputsObj.add(input.name, coerceWidgetValue(providerWidgets[0], inputTypeStr))
                                }
                            } else {
                                val linkArr = JsonArray()
                                linkArr.add(sanitizeId(originId))
                                linkArr.add(originSlot)
                                inputsObj.add(input.name, linkArr)
                            }
                        }
                    }
                }
            }

            nodeObj.add("inputs", inputsObj)

            val metaObj = JsonObject()
            val displayName = nodeMetadataCache[node.type]?.display_name
            val title = if (!displayName.isNullOrEmpty()) displayName else getNodeTitle(node.type)
            metaObj.addProperty("title", title)
            nodeObj.add("_meta", metaObj)

            result.add(sanitizeId(node.id), nodeObj)
        }

        return result
    }

    // ==================== Node Processors ====================

    private fun processPrimitiveNode(inputsObj: JsonObject, widgetsArr: JsonArray) {
        if (widgetsArr.size() > 0) {
            inputsObj.add("value", convertWidgetValue(widgetsArr[0], ParamType.STRING))
        }
        if (widgetsArr.size() > 1) {
            val elem = widgetsArr[1]
            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                inputsObj.add("control_after_generate", elem)
            }
        }
    }

    private fun processKSamplerNode(inputsObj: JsonObject, widgetsArr: JsonArray) {
        val samplers = listOf(
            "euler", "euler_ancestral", "heun", "dpm_2", "dpm_2_ancestral",
            "lms", "dpm_fast", "dpm_adaptive", "dpmpp_2s_ancestral",
            "dpmpp_sde", "dpmpp_2m", "ddpm", "lcm"
        )
        val schedulers = listOf("normal", "karras", "exponential", "sgm_uniform", "simple", "beta")

        if (widgetsArr.size() > 0) {
            val seed = try {
                if (widgetsArr[0].isJsonPrimitive && widgetsArr[0].asJsonPrimitive.isNumber) {
                    widgetsArr[0].asLong
                } else {
                    System.currentTimeMillis()
                }
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
            inputsObj.addProperty("seed", seed)
        }
        if (widgetsArr.size() > 2) {
            val steps = try {
                if (widgetsArr[2].isJsonPrimitive && widgetsArr[2].asJsonPrimitive.isNumber) {
                    widgetsArr[2].asInt
                } else {
                    8
                }
            } catch (_: Exception) {
                8
            }
            inputsObj.addProperty("steps", steps)
        }
        if (widgetsArr.size() > 3) {
            val cfg = try {
                if (widgetsArr[3].isJsonPrimitive && widgetsArr[3].asJsonPrimitive.isNumber) {
                    widgetsArr[3].asFloat
                } else {
                    1.0f
                }
            } catch (_: Exception) {
                1.0f
            }
            addSmartNumber(inputsObj, "cfg", cfg)
        }
        if (widgetsArr.size() > 4) {
            var sampler = "lcm"
            if (widgetsArr[4].isJsonPrimitive) {
                val s = widgetsArr[4].asString
                if (s in samplers) sampler = s
            }
            inputsObj.addProperty("sampler_name", sampler)
        }
        if (widgetsArr.size() > 5) {
            var scheduler = "exponential"
            if (widgetsArr[5].isJsonPrimitive) {
                val s = widgetsArr[5].asString
                if (s in schedulers) scheduler = s
            }
            inputsObj.addProperty("scheduler", scheduler)
        }
        if (widgetsArr.size() > 6) {
            val denoise = try {
                if (widgetsArr[6].isJsonPrimitive && widgetsArr[6].asJsonPrimitive.isNumber) {
                    widgetsArr[6].asFloat
                } else {
                    1.0f
                }
            } catch (_: Exception) {
                1.0f
            }
            addSmartNumber(inputsObj, "denoise", denoise)
        }
    }

    private fun processLoadImageNode(inputsObj: JsonObject, widgetsArr: JsonArray) {
        if (widgetsArr.size() > 0) {
            inputsObj.addProperty("image", widgetsArr[0].asString)
        }
        if (widgetsArr.size() > 1 && !inputsObj.has("image")) {
            inputsObj.add("image", widgetsArr[0])
        }
    }

    private fun processEmptySD3LatentImage(inputsObj: JsonObject, widgetsArr: JsonArray) {
        if (widgetsArr.size() > 0) {
            inputsObj.add("width", widgetsArr[0])
        }
        if (widgetsArr.size() > 1) {
            inputsObj.add("height", widgetsArr[1])
        }
        if (widgetsArr.size() > 2) {
            inputsObj.add("batch_size", widgetsArr[2])
        }
    }

    private fun processUnetLoaderGGUF(inputsObj: JsonObject, widgetsArr: JsonArray) {
        if (widgetsArr.size() > 0) {
            inputsObj.add("unet_name", widgetsArr[0])
        }
    }

    private fun processCLIPLoaderGGUF(inputsObj: JsonObject, widgetsArr: JsonArray) {
        if (widgetsArr.size() > 0) {
            inputsObj.add("clip_name", widgetsArr[0])
        }
        if (widgetsArr.size() > 1) {
            inputsObj.add("type", widgetsArr[1])
        }
    }

    private fun processVAELoader(inputsObj: JsonObject, widgetsArr: JsonArray) {
        if (widgetsArr.size() > 0) {
            inputsObj.add("vae_name", widgetsArr[0])
        }
    }

    private fun processModelSamplingAuraFlow(inputsObj: JsonObject, widgetsArr: JsonArray) {
        if (widgetsArr.size() > 0) {
            inputsObj.add("shift", widgetsArr[0])
        }
    }

    private fun processSamplerCustomNode(inputsObj: JsonObject, widgetsArr: JsonArray) {
        if (widgetsArr.size() > 0) {
            val addNoise = if (widgetsArr[0].isJsonPrimitive && widgetsArr[0].asJsonPrimitive.isBoolean) {
                widgetsArr[0].asBoolean
            } else {
                true
            }
            inputsObj.addProperty("add_noise", addNoise)
        }
        if (widgetsArr.size() > 1) {
            val noiseSeed = try {
                if (widgetsArr[1].isJsonPrimitive && widgetsArr[1].asJsonPrimitive.isNumber) {
                    widgetsArr[1].asLong
                } else {
                    System.currentTimeMillis()
                }
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
            inputsObj.addProperty("noise_seed", noiseSeed)
        }
        if (widgetsArr.size() > 3) {
            var cfg = 1.0f
            try {
                if (widgetsArr[3].isJsonPrimitive && widgetsArr[3].asJsonPrimitive.isNumber) {
                    cfg = widgetsArr[3].asFloat
                }
            } catch (_: Exception) {
            }
            addSmartNumber(inputsObj, "cfg", cfg)
        }
    }

    private fun processStandardNode(
        inputsObj: JsonObject,
        widgetsArr: JsonArray,
        widgetLabels: Map<Int, String>,
        nodeType: String,
        nodeInputs: List<UiInput>
    ): Boolean {
        val meta = nodeMetadataCache[nodeType] ?: return false
        var orderedNames = meta.input_order?.required ?: emptyList()
        if (orderedNames.isEmpty()) {
            val keys = meta.input?.required?.keys?.toList()
            if (keys != null && keys.isNotEmpty()) {
                orderedNames = keys
                Log.d(TAG, "processStandardNode($nodeType): using input.required keys as fallback order: $orderedNames")
            }
        }
        if (orderedNames.isEmpty()) {
            Log.d(TAG, "processStandardNode($nodeType): no metadata available, returning false")
            return false
        }
        val inputMap = meta.input?.required ?: emptyMap()
        var i = 0
        for (paramName in orderedNames) {
            if (i >= widgetsArr.size()) return true
            val pair = inputMap[paramName]
            val typeStr = pair?.first
            if (typeStr != null && isWidgetType(typeStr)) {
                val widgetValue = widgetsArr[i]
                i++
                if (paramName in UI_ONLY_WIDGETS) {
                    i = skipControlAfterGenerate(widgetsArr, i, typeStr)
                    continue
                }
                if (inputsObj.has(paramName)) {
                    i = skipControlAfterGenerate(widgetsArr, i, typeStr)
                    continue
                }
                val paramType = getParamType(nodeType, paramName)
                inputsObj.add(paramName, convertWidgetValue(widgetValue, paramType))
                i = skipControlAfterGenerate(widgetsArr, i, typeStr)
            }
        }
        return true
    }

    private fun processSmartGenericNode(
        inputsObj: JsonObject,
        widgetsArr: JsonArray,
        nodeInputs: List<UiInput>
    ): Boolean {
        val widgetInputs = nodeInputs.filter { it.widget != null }
        if (widgetInputs.isEmpty() || widgetsArr.size() == 0) return false

        val controlCount = (0 until widgetsArr.size()).count { idx ->
            val elem = widgetsArr[idx]
            elem.isJsonPrimitive && elem.asJsonPrimitive.isString &&
                    elem.asString in CONTROL_AFTER_GENERATE_VALUES
        }

        if (widgetInputs.size + controlCount != widgetsArr.size() &&
            widgetInputs.size != widgetsArr.size() &&
            widgetsArr.size() < widgetInputs.size
        ) {
            return false
        }

        var i = 0
        for (uiInput in widgetInputs) {
            if (i >= widgetsArr.size()) break
            val widget = uiInput.widget
            var name = uiInput.name
            if (widget is JsonObject && widget.has("name")) {
                name = widget.get("name").asString
            }
            val jsonElement = widgetsArr[i]
            val next = i + 1
            if ((uiInput.link == null || uiInput.link.isJsonNull) &&
                !inputsObj.has(name) &&
                name !in UI_ONLY_WIDGETS
            ) {
                val typeStr = if (uiInput.type.isJsonPrimitive) uiInput.type.asString else null
                inputsObj.add(name, coerceWidgetValue(jsonElement, typeStr))
            }
            i = if (next < widgetsArr.size() && widgetsArr[next].isJsonPrimitive &&
                widgetsArr[next].asJsonPrimitive.isString &&
                widgetsArr[next].asString in CONTROL_AFTER_GENERATE_VALUES
            ) {
                next + 1
            } else next
        }
        return i > 0
    }

    private fun processRawLabelFallback(
        inputsObj: JsonObject,
        widgetsArr: JsonArray,
        widgetLabels: Map<Int, String>
    ) {
        for (i in 0 until widgetsArr.size()) {
            val elem = widgetsArr[i]
            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString &&
                elem.asString in CONTROL_AFTER_GENERATE_VALUES
            ) {
                continue
            }
            val label = widgetLabels[i] ?: "widget_$i"
            if (!inputsObj.has(label) && label !in UI_ONLY_WIDGETS) {
                inputsObj.add(label, elem)
            }
        }
    }

    // ==================== Helpers ====================

    private suspend fun loadNodeMetadata(nodeType: String) {
        val cached = nodeMetadataCache[nodeType]
        if (cached != null) {
            val hasInput = cached.input?.required?.isNotEmpty() == true
            val hasOrder = cached.input_order?.required?.isNotEmpty() == true
            if (hasInput || hasOrder) return
        }
        val info = nodeMetadataManager.loadMetadata(nodeType)
        if (info != null) {
            Log.d(TAG, "loadNodeMetadata($nodeType): SUCCESS")
            nodeMetadataCache[nodeType] = info
            val paramTypes = mutableMapOf<String, ParamType>()
            info.input?.required?.forEach { (name, pair) ->
                paramTypes[name] = resolveParamType(pair.first)
            }
            info.input?.optional?.forEach { (name, pair) ->
                paramTypes[name] = resolveParamType(pair.first)
            }
            nodeParamTypesCache[nodeType] = paramTypes
        } else {
            Log.w(TAG, "loadNodeMetadata($nodeType): FAILED")
        }
    }

    private fun extractWidgetLabels(nodeTypeInfo: NodeTypeInfo?): Map<Int, String> {
        if (nodeTypeInfo == null) return emptyMap()
        val result = mutableMapOf<Int, String>()
        var index = 0
        val inputOrder = nodeTypeInfo.input_order
        val inputInfo = nodeTypeInfo.input

        val orderedNames = mutableListOf<String>()
        inputOrder?.required?.let { orderedNames.addAll(it) }
        inputOrder?.optional?.let { orderedNames.addAll(it) }
        inputOrder?.hidden?.let { orderedNames.addAll(it) }

        if (orderedNames.isEmpty()) {
            inputInfo?.required?.keys?.let { orderedNames.addAll(it) }
            inputInfo?.optional?.keys?.let { orderedNames.addAll(it) }
        }

        for (name in orderedNames) {
            val paramInfo = inputInfo?.required?.get(name) ?: inputInfo?.optional?.get(name)
            if (paramInfo != null) {
                val options = paramInfo.second
                if (!options.forceInput) {
                    result[index] = name
                    index++
                }
            }
        }
        return result
    }

    private fun getParamType(nodeType: String, paramName: String): ParamType {
        return nodeParamTypesCache[nodeType]?.get(paramName) ?: ParamType.STRING
    }

    private fun resolveParamType(typeStr: String): ParamType {
        return when {
            typeStr.startsWith("[") && typeStr.endsWith("]") -> ParamType.ENUM
            typeStr.contains(",") -> ParamType.ENUM
            typeStr.equals("ENUM", ignoreCase = true) -> ParamType.ENUM
            typeStr.equals("INT", ignoreCase = true) || typeStr.equals("INTEGER", ignoreCase = true) -> ParamType.INT
            typeStr.equals("FLOAT", ignoreCase = true) -> ParamType.FLOAT
            typeStr.equals("STRING", ignoreCase = true) -> ParamType.STRING
            typeStr.equals("BOOLEAN", ignoreCase = true) || typeStr.equals("BOOL", ignoreCase = true) -> ParamType.BOOLEAN
            else -> if (isWidgetType(typeStr)) ParamType.STRING else ParamType.CONNECTION
        }
    }

    private fun isWidgetType(typeStr: String): Boolean {
        val upper = typeStr.trim().uppercase()
        return upper in setOf("INT", "FLOAT", "STRING", "BOOLEAN", "BOOL", "ENUM", "NUMBER", "TEXT")
    }

    private fun convertWidgetValue(jsonElement: JsonElement, paramType: ParamType): JsonElement {
        if (paramType == ParamType.CONNECTION) return jsonElement
        return when (paramType) {
            ParamType.INT -> {
                if (jsonElement.isJsonPrimitive && jsonElement.asJsonPrimitive.isNumber) {
                    jsonElement
                } else {
                    try {
                        JsonPrimitive(jsonElement.asString.toInt())
                    } catch (_: Exception) {
                        JsonPrimitive(0)
                    }
                }
            }
            ParamType.FLOAT -> {
                if (jsonElement.isJsonPrimitive && jsonElement.asJsonPrimitive.isNumber) {
                    jsonElement
                } else {
                    try {
                        JsonPrimitive(jsonElement.asString.toFloat())
                    } catch (_: Exception) {
                        JsonPrimitive(0.0f)
                    }
                }
            }
            ParamType.BOOLEAN -> {
                if (jsonElement.isJsonPrimitive && jsonElement.asJsonPrimitive.isBoolean) {
                    jsonElement
                } else {
                    JsonPrimitive(jsonElement.asString.equals("true", ignoreCase = true))
                }
            }
            ParamType.ENUM, ParamType.STRING -> {
                if (!jsonElement.isJsonPrimitive) {
                    JsonPrimitive(jsonElement.toString().replace("\"", ""))
                } else {
                    jsonElement
                }
            }
            else -> jsonElement
        }
    }

    private fun coerceWidgetValue(jsonElement: JsonElement, inputType: String?): JsonElement {
        if (jsonElement.isJsonNull) return jsonElement
        val upper = inputType?.uppercase() ?: ""
        return try {
            when (upper) {
                "STRING" -> if (jsonElement.isJsonPrimitive) jsonElement else JsonPrimitive(jsonElement.toString())
                "INT", "INTEGER" -> {
                    if (jsonElement.isJsonPrimitive && jsonElement.asJsonPrimitive.isNumber) {
                        jsonElement
                    } else {
                        try {
                            JsonPrimitive(jsonElement.asString.toLong())
                        } catch (_: Exception) {
                            jsonElement
                        }
                    }
                }
                "FLOAT" -> {
                    if (jsonElement.isJsonPrimitive && jsonElement.asJsonPrimitive.isNumber) {
                        jsonElement
                    } else if (jsonElement.isJsonPrimitive && jsonElement.asJsonPrimitive.isString) {
                        try {
                            JsonPrimitive(jsonElement.asString.toDouble())
                        } catch (_: Exception) {
                            jsonElement
                        }
                    } else {
                        jsonElement
                    }
                }
                "BOOLEAN", "BOOL" -> {
                    if (jsonElement.isJsonPrimitive && jsonElement.asJsonPrimitive.isBoolean) {
                        jsonElement
                    } else {
                        JsonPrimitive(jsonElement.asString.equals("true", ignoreCase = true))
                    }
                }
                else -> {
                    if (jsonElement.isJsonPrimitive) {
                        val primitive = jsonElement.asJsonPrimitive
                        if (primitive.isNumber) {
                            val d = primitive.asDouble
                            val l = d.toLong()
                            if (d != l.toDouble() || d >= Long.MAX_VALUE) {
                                jsonElement
                            } else {
                                JsonPrimitive(l)
                            }
                        } else {
                            jsonElement
                        }
                    } else {
                        jsonElement
                    }
                }
            }
        } catch (_: Exception) {
            jsonElement
        }
    }

    private fun addSmartNumber(inputsObj: JsonObject, key: String, number: Number) {
        if (number.toDouble() % 1.0 == 0.0) {
            inputsObj.addProperty(key, number.toLong())
        } else {
            inputsObj.addProperty(key, number)
        }
    }

    private fun skipControlAfterGenerate(widgetsArr: JsonArray, index: Int, typeStr: String): Int {
        val upper = typeStr.uppercase()
        if ((upper == "INT" || upper == "INTEGER" || upper == "SEED" || upper == "LONG") && index < widgetsArr.size()) {
            val elem = widgetsArr[index]
            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString && elem.asString in CONTROL_AFTER_GENERATE_VALUES) {
                return index + 1
            }
        }
        return index
    }

    private fun findUltimateSource(
        sourceNodeId: String,
        sourceSlot: Int,
        expectedType: String,
        excludedNodes: Set<String>,
        nodeInputSlots: Map<String, Map<Int, Int>>,
        linkMap: Map<Int, UiLink>,
        uiWorkflow: UiWorkflow,
        rerouteNodeIds: Set<String>
    ): Pair<String, Int>? {
        var currentId = sourceNodeId
        var currentSlot = sourceSlot
        val visited = mutableSetOf<String>()

        while (true) {
            if (currentId in visited) return null
            visited.add(currentId)

            if (currentId !in excludedNodes && currentId !in rerouteNodeIds) {
                return currentId to currentSlot
            }

            if (currentId in rerouteNodeIds) {
                val slotMap = nodeInputSlots[currentId] ?: return null
                val linkId = slotMap[0] ?: return null
                val link = linkMap[linkId] ?: return null
                currentId = link.origin_id
                currentSlot = link.origin_slot
            } else if (currentId in excludedNodes) {
                val node = uiWorkflow.nodes.find { it.id == currentId } ?: return null
                val matchingInput = node.inputs.find { input ->
                    val typeStr = if (input.type.isJsonPrimitive) input.type.asString else input.type.toString()
                    typeStr == expectedType
                } ?: run {
                    Log.w(TAG, "No matching input found for type $expectedType in excluded node $currentId")
                    return null
                }
                val inputIdx = node.inputs.indexOf(matchingInput)
                val slotMap = nodeInputSlots[currentId] ?: return null
                val linkId = slotMap[inputIdx] ?: return null
                val link = linkMap[linkId] ?: return null
                currentId = link.origin_id
                currentSlot = link.origin_slot
            }
        }
    }

    private fun sanitizeId(id: String): String {
        return if (id.endsWith(".0")) id.substringBefore(".0") else id
    }

    private fun getNodeTitle(type: String): String {
        return when (type) {
            "SaveImage" -> "Save Image"
            "LoraLoader" -> "Load LoRA"
            "LoadImage" -> "Load Image"
            "VAEDecode" -> "VAE Decode"
            "CheckpointLoaderSimple" -> "Load Checkpoint"
            "ReActorFaceSwap" -> "ReActor \uD83C\uDF0C Fast Face Swap"
            "EmptyLatentImage" -> "Empty Latent Image"
            "KSampler" -> "KSampler"
            "CLIPTextEncode" -> "CLIP Text Encode (Prompt)"
            else -> type
        }
    }
}
