/*
Copyright 2024 SkillTree

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
<script setup>
import { computed, ref } from 'vue'
import {boolean, object, string, ValidationError} from 'yup'
import { useDebounceFn } from '@vueuse/core'
import ProjectService from '@/components/projects/ProjectService.js'
import { useAppConfig } from '@/common-components/stores/UseAppConfig.js'
import { useCommunityLabels } from '@/components/utils/UseCommunityLabels.js'
import SkillsInputFormDialog from '@/components/utils/inputForm/SkillsInputFormDialog.vue'
import { useAccessState } from '@/stores/UseAccessState.js'
import CommunityProtectionControls from '@/components/projects/CommunityProtectionControls.vue'
import { useDescriptionValidatorService } from '@/common-components/validators/UseDescriptionValidatorService.js'
import FileUpload from 'primevue/fileupload'

const model = defineModel()
const props = defineProps(['project', 'isEdit', 'isCopy'])
const emit = defineEmits(['project-generated'])
const accessState = useAccessState()

let formId = 'generateProjectDialog'
let modalTitle = 'Generate Project'
const appConfig = useAppConfig()

const jsonText = ref('')
const hostedFileName = ref(null)

const communityLabels = useCommunityLabels()
const initialValueForEnableProtectedUserCommunity = communityLabels.isRestrictedUserCommunity(props.project.userCommunity)
const enableProtectedUserCommunity = ref(initialValueForEnableProtectedUserCommunity)
const userCommunityDescriptor = computed(() => {
  return enableProtectedUserCommunity.value ? appConfig.userCommunityRestrictedDescriptor : appConfig.defaultCommunityDescriptor
})
const userCommunityVal = computed(() => {
    if (props.isEdit) {
      return enableProtectedUserCommunity.value ? userCommunityDescriptor.value : props.project.userCommunity
    }
    return userCommunityDescriptor.value
})

const checkProjNameUnique = useDebounceFn((value) => {
  if (!value || value.length === 0) {
    return true
  }
  const origName = props.project.name
  if (props.isEdit && (origName === value || origName.localeCompare(value, 'en', { sensitivity: 'base' }) === 0)) {
    return true
  }
  return ProjectService.checkIfProjectNameExist(value).then((remoteRes) => !remoteRes)
}, appConfig.formFieldDebounceInMs)
const checkProjIdUnique = useDebounceFn((value) => {
  if (!value || value.length === 0 || (props.isEdit && props.project.projectId === value)) {
    return true
  }
  return ProjectService.checkIfProjectIdExist(value)
    .then((remoteRes) => !remoteRes)

}, appConfig.formFieldDebounceInMs)

const descriptionValidatorService = useDescriptionValidatorService()
const checkDescription = useDebounceFn((value, testContext) => {
  if (!value || value.trim().length === 0 || !appConfig.paragraphValidationRegex) {
    return true
  }
  return descriptionValidatorService.validateDescription(value, false, enableProtectedUserCommunity.value, false).then((result) => {
    if (result.valid) {
      return true
    }
    let fieldNameToUse = 'Project Description'
    if (result.msg) {
      return testContext.createError({ message: `${fieldNameToUse ? `${fieldNameToUse} - ` : ''}${result.msg}` })
    }
    return testContext.createError({ message: `${fieldNameToUse || 'Field'} is invalid` })
  })

}, appConfig.formFieldDebounceInMs)


const checkProjectCommunityRequirements =(value, testContext) => {
  if (!value || !props.isEdit) {
    return true;
  }
  return ProjectService.validateProjectForEnablingCommunity(props.project.projectId).then((result) => {
    if (result.isAllowed) {
      return true;
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
  });
}

const schema = object({
  // 'projectName': string()
  //   .trim()
  //   .required()
  //   .min(appConfig.minNameLength)
  //   .max(appConfig.maxProjectNameLength)
  //   .nullValueNotAllowed()
  //   .test('uniqueName', 'Project Name already exists', (value) => checkProjNameUnique(value))
  //   .customNameValidator('Project Name')
  //   .label('Project Name'),
  // 'projectId': string()
  //   .required()
  //   .min(appConfig.minIdLength)
  //   .max(appConfig.maxIdLength)
  //   .idValidator()
  //   .nullValueNotAllowed()
  //   .test('uniqueId', 'Project ID already exists', (value) => checkProjIdUnique(value))
  //   .label('Project ID'),
  'enableProtectedUserCommunity': boolean()
    .test('communityReqValidation', 'Unmet community requirements', (value, testContext) => checkProjectCommunityRequirements(value, testContext))
    .label('Enable Protected User Community'),
  // 'description': string()
  //   .max(appConfig.descriptionMaxLength)
  //   .test('descriptionValidation', 'Description is invalid', (value, testContext) => checkDescription(value, testContext))
  //   .label('Project Description')
  // 'jsonFileName': string()
  //     .trim()
  //     .required()
  //     // .min(appConfig.minNameLength)
  //     // .max(appConfig.maxProjectNameLength)
  //     .nullValueNotAllowed()
  //     // .test('uniqueName', 'Project Name already exists', (value) => checkProjNameUnique(value))
  //     // .customNameValidator('Project Name')
  //     .label('JSON File Name'),
  //
  // 'jsonText': string()
  //     .trim()
  //     .required()
  //     // .min(appConfig.minNameLength)
  //     // .max(appConfig.maxProjectNameLength)
  //     .nullValueNotAllowed()
  //     // .test('uniqueName', 'Project Name already exists', (value) => checkProjNameUnique(value))
  //     // .customNameValidator('Project Name')
  //     .label('Project JSON'),
  //
  // 'jsonFile': yup.object()
  //     .nullable()
  //     // .required()
  //     // .test('videoMimeTypesValidation', (value, context) => slidesMimeTypesValidation(value, context))
  //     // .test('videoMaxSizeValidation', (value, context) => slidesMaxSizeValidation(value, context))
  //     .label('File'),
})

const initialProjData = ref({
  projectId: props.project.projectId || '',
  projectName: props.project.name || '',
  description: props.project.description || '',
  enableProtectedUserCommunity: false,
})

const close = () => { model.value = false }

const isRootUser = computed(() => accessState.isRoot)
const generateProject = (values) => {
  const generatedProject = JSON.parse(jsonText.value)
  if (initialValueForEnableProtectedUserCommunity) {
    generatedProject.project.enableProtectedUserCommunity =
      initialValueForEnableProtectedUserCommunity
  }

  emit('project-generated', generatedProject)
  return Promise.resolve()
}

const onSavedProject = () => {
  close()
}

const openFileDialog = (event) => {
  const jsonFileInput = document.getElementById('jsonFileInput');
  if (jsonFileInput) {
    jsonFileInput.click();
  }
}

const onFileSelectedEvent = (selectEvent) => {
  const selectedJsonFile = selectEvent.files[0];
  // console.log('selectedJsonFile', selectedJsonFile);
  selectedJsonFile.text().then((data) => {
    // console.log('jsonText', data)
    jsonText.value = data
    hostedFileName.value = selectedJsonFile.name;
  })
}

</script>

<template>
  <SkillsInputFormDialog
    :id="formId"
    v-model="model"
    :should-confirm-cancel="true"
    :is-edit="isEdit"
    :header="modalTitle"
    :saveButtonLabel="`${isCopy ? 'Copy Project' : 'Save'}`"
    :validation-schema="schema"
    :initial-values="initialProjData"
    :ok-button-disabled="!jsonText"
    @saved="onSavedProject"
    @close="close"
    :save-data-function="generateProject">
    <template #default>
      <community-protection-controls
        v-model:enable-protected-user-community="enableProtectedUserCommunity"
        :project="project"
        :is-edit="isEdit"
        :is-copy="isCopy" />

      <div>
        <InputGroup>
          <InputText :pt="{ root: { readOnly: true } }"
                     id="jsonFileInputDropTarget"
                     data-cy="jsonFileInputDropTarget"
                     variant="filled"
                     v-model="hostedFileName"
                     @click="openFileDialog"
                     placeholder="Upload file from my computer by clicking Browse or drag-n-dropping it here..."/>
          <InputGroupAddon>
            <FileUpload
                :pt="{ root: { class: 'border-round-right border-l-0 bg-primary' }, input: { id: 'jsonFileInput'} }"
                data-cy="jsonFileUpload"
                mode="basic"
                :auto="true"
                :show-upload-button="false"
                :custom-upload="true"
                @uploader=""
                @select="onFileSelectedEvent"
                chooseLabel="Browse"/>
          </InputGroupAddon>
        </InputGroup>
      </div>
      <!--      <div>-->
      <!--        <div v-if="jsonText">-->
      <!--          <h3>Processed Data:</h3>-->
      <!--          <pre>{{ jsonText }}</pre>-->
      <!--        </div>-->
      <!--      </div>-->
    </template>
  </SkillsInputFormDialog>
</template>

<style scoped>

</style>