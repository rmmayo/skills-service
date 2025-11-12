/* Copyright 2025 SkillTree Licensed under the Apache License, Version 2.0 (the "License"); you may
not use this file except in compliance with the License. You may obtain a copy of the License at
https://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in
writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License. */
<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownText from '@/common-components/utilities/markdown/MarkdownText.vue'
import { useLog } from '@/components/utils/misc/useLog.js'
import { useImgHandler } from '@/common-components/utilities/learning-conent-gen/UseImgHandler.js'
import { useInstructionGenerator } from '@/common-components/utilities/learning-conent-gen/UseInstructionGenerator.js'
import { useDescriptionValidatorService } from '@/common-components/validators/UseDescriptionValidatorService.js'
import { useAppConfig } from '@/common-components/stores/UseAppConfig.js'
import AiPromptDialog from '@/common-components/utilities/learning-conent-gen/AiPromptDialog.vue'
import AiKnowledgeStore from '@/common-components/utilities/learning-conent-gen/AiKnowledgeStore.vue'
import { useOpenaiService } from '@/common-components/utilities/learning-conent-gen/UseOpenaiService.js'

const model = defineModel()
const props = defineProps({
  communityValue: {
    type: String,
    default: null
  }
})
const emit = defineEmits(['use-generated'])
const route = useRoute()
const log = useLog()
const imgHandler = useImgHandler()
const appConfig = useAppConfig()
const instructionsGenerator = useInstructionGenerator()
const openaiService = useOpenaiService()

const currentDescription = ref('')
const extractedImageState = { hasImages: false, extractedImages: null }

const userChatSettings = ref(null)
const aiPromptDialogRef = ref(null)
const updateDescription = (newDesc) => {
  currentDescription.value = newDesc

  const welcomeMsg = newDesc
    ? "I noticed you've already started and can help you refine and enhance! \n\nFor example, you could type `proofread` or `rewrite with more detail`. The more **specific** you are, the better I can assist you!"
    : "Hi there! I'm excited to help you craft something **amazing**. Please share some `details` about what you have in mind."

  aiPromptDialogRef.value.addWelcomeMsg(welcomeMsg)
}
const chatHasResponded = ref(false)
const followOnInstructions = computed(() => {
  return userChatSettings.value?.conversationId || chatHasResponded.value
})
defineExpose({
  updateDescription
})
const knowledgeStoreLoading = ref(true)
const loadingUserChatSettings = ref(true)
const isLoading = computed(() => {
  return knowledgeStoreLoading.value || loadingUserChatSettings.value
})
onMounted(() => {
  openaiService.getUserChatSettings().then((res) => {
    userChatSettings.value = res
    loadingUserChatSettings.value = false
    updateDescription()
  })
})

const createPromptInstructions = (userEnterInstructions) => {
  let instructionsToSend = userEnterInstructions
  if (followOnInstructions.value) {
    instructionsToSend = `
### User Instructions: ${userEnterInstructions}

Don't forget that this is a user-friendly preview of the training that you will generate that will be show to the user for review as markdown using the previously entered system instructions.
  `
  }
  return instructionsToSend
}

const handleGeneratedChunk = (chunk) => {
  return {
    append: true,
    chunk: chunk
  }
}

const handleGenerationCompleted = (generated) => {
  chatHasResponded.value = true
  let generatedValue = generated.generatedValue
  let generateValueChangedNotes = null

  const [newText, comments] = generatedValue.split('Here is what was changed')
  if (newText && comments) {
    const cleanedComments = comments.replace(/^[\s:]+/, '').trim()
    generatedValue = newText.replace(/\s*#+\s*$/gm, '') // Remove ### at end of lines.trim()
    generateValueChangedNotes = `### Here is what was changed\n\n${cleanedComments}`
  }

  if (extractedImageState.extractedImages) {
    const { text, unusedImages } = imgHandler.reinsertImages(
      generatedValue,
      extractedImageState.extractedImages
    )
    generatedValue = text
    extractedImageState.extractedImages = unusedImages
  }

  return {
    generatedValue,
    generateValueChangedNotes
  }
}

const useGenerated = async (historyItem) => {
  emit('use-generated')
}

const validationService = useDescriptionValidatorService()
const handleAddPrefix = (historyItem, missingPrefix) => {
  if (historyItem?.generatedValue) {
    return validationService
      .addPrefixToInvalidParagraphs(historyItem?.generatedValue, missingPrefix)
      .then((result) => {
        historyItem.generatedValue = result.newDescription
        return historyItem
      })
  }

  return historyItem
}
</script>

<template>
  <ai-prompt-dialog
    ref="aiPromptDialogRef"
    v-model="model"
    :create-instructions-fn="createPromptInstructions"
    :chunk-handler-fn="handleGeneratedChunk"
    :generation-completed-fn="handleGenerationCompleted"
    :add-prefix-fn="handleAddPrefix"
    :community-value="communityValue"
    :generateProject="true"
    :useGeneratedLabel="'Generate Training'"
    :loading-additional-data="isLoading"
    @use-generated="useGenerated">
    <template #onTop>
      <ai-knowledge-store v-if="!loadingUserChatSettings" :vector-store-id="userChatSettings.vectorStoreId" @knowledge-store-loaded="knowledgeStoreLoading=false"/>
    </template>
    <template #generatedValue="{ historyItem }">
      <markdown-text
        :text="historyItem.generatedValue"
        data-cy="generatedSegment"
        :instanceId="`${historyItem.id}-desc`" />
    </template>
  </ai-prompt-dialog>
</template>

<style scoped></style>