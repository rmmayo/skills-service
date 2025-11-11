/* Copyright 2024 SkillTree Licensed under the Apache License, Version 2.0 (the "License"); you may
not use this file except in compliance with the License. You may obtain a copy of the License at
https://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in
writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License. */
<script setup>
import { computed, ref } from 'vue'
import { boolean, object, ValidationError } from 'yup'
import ProjectService from '@/components/projects/ProjectService.js'
import { useAppConfig } from '@/common-components/stores/UseAppConfig.js'
import { useCommunityLabels } from '@/components/utils/UseCommunityLabels.js'
import SkillsInputFormDialog from '@/components/utils/inputForm/SkillsInputFormDialog.vue'
import { useAccessState } from '@/stores/UseAccessState.js'
import CommunityProtectionControls from '@/components/projects/CommunityProtectionControls.vue'
import FileUpload from 'primevue/fileupload'
import FileUploadService from '@/common-components/utilities/FileUploadService.js'
import { useInstructionGenerator } from '@/common-components/utilities/learning-conent-gen/UseInstructionGenerator.js'

const model = defineModel()
const props = defineProps(['project', 'isEdit', 'isCopy'])
const emit = defineEmits(['project-generated'])
const accessState = useAccessState()

let formId = 'generateProjectDialog'
let modalTitle = 'Generate Project'
const appConfig = useAppConfig()
const instructionsGenerator = useInstructionGenerator()

const generateProjConf = ref({
  files: [],
  hostedFileName: ''
})

const communityLabels = useCommunityLabels()
const initialValueForEnableProtectedUserCommunity = communityLabels.isRestrictedUserCommunity(
  props.project.userCommunity
)
const enableProtectedUserCommunity = ref(initialValueForEnableProtectedUserCommunity)
const userCommunityDescriptor = computed(() => {
  return enableProtectedUserCommunity.value
    ? appConfig.userCommunityRestrictedDescriptor
    : appConfig.defaultCommunityDescriptor
})
const userCommunityVal = computed(() => {
  if (props.isEdit) {
    return enableProtectedUserCommunity.value
      ? userCommunityDescriptor.value
      : props.project.userCommunity
  }
  return userCommunityDescriptor.value
})

const checkProjectCommunityRequirements = (value, testContext) => {
  if (!value || !props.isEdit) {
    return true
  }
  return ProjectService.validateProjectForEnablingCommunity(props.project.projectId).then(
    (result) => {
      if (result.isAllowed) {
        return true
      }
      if (result.unmetRequirements) {
        // return `<ul><li>${result.unmetRequirements.join('</li><li>')}</li></ul>`;
        const errors = result.unmetRequirements.map((req) => {
          return testContext.createError({ message: `${req}` })
        })
        return new ValidationError(errors)
      }
      // return testContext.createError({ message: `${fieldNameToUse ? `${fieldNameToUse} - ` : ''}${result.msg}` })
      // return '{_field_} is invalid.';
      return true
    }
  )
}

const schema = object({
  enableProtectedUserCommunity: boolean()
    .test('communityReqValidation', 'Unmet community requirements', (value, testContext) =>
      checkProjectCommunityRequirements(value, testContext)
    )
    .label('Enable Protected User Community')
})

const initialProjData = ref({
  projectId: props.project.projectId || '',
  projectName: props.project.name || '',
  description: props.project.description || '',
  enableProtectedUserCommunity: false
})

const close = () => {
  model.value = false
}

const isRootUser = computed(() => accessState.isRoot)
const uploadTrainingDocuments = async (values) => {
  const formData = new FormData()
  for (let i = 0; i < generateProjConf.value.files.length; i++) {
    // The name 'files' must match the @RequestParam name in the Spring controller
    formData.append('files', generateProjConf.value.files[i])
  }

  const response = await uploadAndStore(formData)
  console.log(`generated project response`, response?.data)
  const generatedProject = extractJsonFromString(response?.data)
  if (initialValueForEnableProtectedUserCommunity) {
    generatedProject.project.enableProtectedUserCommunity =
      initialValueForEnableProtectedUserCommunity
  }

  // emit('project-generated', generatedProject)
  return Promise.resolve()
}

const uploadAndStore = async (formData) => {
  const endpoint = '/openai/uploadAndStore'
  return FileUploadService.asyncUpload(endpoint, formData)
}
const extractJsonFromString = (text) => {
  const regex = /```json\s*([\s\S]*?)\s*```/
  const match = text.match(regex)
  if (match && match[1]) {
    let jsonString = match[1].trim()

    // Clean up: Remove any trailing commas that would invalidate the JSON,
    // like the comma in '}, }' which is present in the example data.
    jsonString = jsonString.replace(/,\s*\}/g, '}')

    // Clean up: Replace non-breaking space characters (\u00A0) which sometimes creep into copied text.
    jsonString = jsonString.replace(/\u00A0/g, ' ')

    try {
      // 2. Parse the cleaned string into a JavaScript object
      console.log('attempting to parse JSON', jsonString)
      return JSON.parse(jsonString)
    } catch (e) {
      console.error(
        'Error parsing JSON. Check the structure for syntax errors or invalid characters.',
        e
      )
      return null
    }
  }

  // No JSON structure found
  console.log("No '```json' code block found in the string.")
  return null
}

const onGenerateProjected = () => {
  close()
}

const openFileDialog = (event) => {
  const jsonFileInput = document.getElementById('jsonFileInput')
  if (jsonFileInput) {
    jsonFileInput.click()
  }
}

const onFileSelectedEvent = (selectEvent) => {
  generateProjConf.value.files = selectEvent.files
  generateProjConf.value.hostedFileName = selectEvent.files.map((file) => file.name).join(', ')
  console.log('generateProjConf.value', generateProjConf.value)
}
</script>

<template>
  <SkillsInputFormDialog
    :id="formId"
    v-model="model"
    :should-confirm-cancel="true"
    :is-edit="isEdit"
    :header="modalTitle"
    :saveButtonLabel="`${isCopy ? 'Copy Project' : 'Generate Project'}`"
    :validation-schema="schema"
    :initial-values="initialProjData"
    :ok-button-disabled="!generateProjConf.hostedFileName"
    @saved="onGenerateProjected"
    @close="close"
    :save-data-function="uploadTrainingDocuments">
    <template #default>
      <community-protection-controls
        v-model:enable-protected-user-community="enableProtectedUserCommunity"
        :project="project"
        :is-edit="isEdit"
        :is-copy="isCopy" />

      <div>
        <InputGroup>
          <InputText
            :pt="{ root: { readOnly: true } }"
            id="jsonFileInputDropTarget"
            data-cy="jsonFileInputDropTarget"
            variant="filled"
            v-model="generateProjConf.hostedFileName"
            @click="openFileDialog"
            placeholder="Upload file from my computer by clicking Browse or drag-n-dropping it here..." />
          <InputGroupAddon>
            <FileUpload
              :pt="{
                root: { class: 'border-round-right border-l-0 bg-primary' },
                input: { id: 'jsonFileInput' }
              }"
              data-cy="jsonFileUpload"
              mode="basic"
              :multiple="true"
              :auto="true"
              :show-upload-button="false"
              :custom-upload="true"
              @uploader=""
              @select="onFileSelectedEvent"
              chooseLabel="Browse" />
          </InputGroupAddon>
        </InputGroup>
      </div>
    </template>
  </SkillsInputFormDialog>
</template>

<style scoped></style>
