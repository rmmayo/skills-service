<script setup>
import {computed, onMounted, ref} from 'vue'
import {FileUpload} from "primevue";
import {usePrimeVue} from 'primevue/config';
import {useToast} from "primevue/usetoast";
import FileUploadService from "@/common-components/utilities/FileUploadService.js";
import {useOpenaiService} from "@/common-components/utilities/learning-conent-gen/UseOpenaiService.js";
import SkillsSpinner from "@/components/utils/SkillsSpinner.vue";
import SkillsButton from "@/components/utils/inputForm/SkillsButton.vue";

const $primevue = usePrimeVue();
const toast = useToast();

const totalSize = ref(0);
const totalSizePercent = ref(0);
const currentKnowledgeFiles = ref([])
const files = ref([]);
const initLoading = ref(true)
const manageFiles = ref(false)

const isStoreEmpty = computed(() => currentKnowledgeFiles.value.length === 0)
const showFileUpload = ref(false)

const openAiService = useOpenaiService()

onMounted(() => {
  loadKnowledgeStore().finally(() => initLoading.value = false)
})

const onRemoveTemplatingFile = (file, removeFileCallback, index) => {
  removeFileCallback(index);
  // totalSize.value -= parseInt(formatSize(file.size));
  // totalSizePercent.value = totalSize.value / 10;
  files.value = files.value.filter((f) => f.name !== file.name)
};

const loadKnowledgeStore = () => {
  return openAiService.getKnowledgeStoreFiles().then((response) => {
    currentKnowledgeFiles.value = response.filter((it) => it.filename !== 'SkillTreeConcepts.pdf')
    console.log('loaded knowledge store')
  })
}

const onClearTemplatingUpload = (clear) => {
  clear();
  totalSize.value = 0;
  totalSizePercent.value = 0;
};

const onSelectedFiles = (event) => {
  files.value = event.files;
  files.value.forEach((file) => {
    totalSize.value += parseInt(formatSize(file.size));
  });
};

const incrementProgress = ()  => {
  setTimeout(() => {
    totalSizePercent.value = totalSizePercent.value + 5
    if (showFileUpload.value) {
      incrementProgress()
    }
  }, 100);
}

const uploadEvent = (callback) => {
  totalSizePercent.value = totalSize.value / 10;
  callback();
  incrementProgress()
  return uploadTrainingDocuments().then((response) => {
    totalSizePercent.value = 100;
    currentKnowledgeFiles.value.push(...response.files.filter((it) => it.filename !== 'SkillTreeConcepts.pdf'))
    files.value = []
    showFileUpload.value = false
    totalSizePercent.value = 0
    console.log('done uploading')
  })
};

const uploadTrainingDocuments = () => {
  const formData = new FormData()
  for (let i = 0; i < files.value.length; i++) {
    formData.append('files', files.value[i])
  }
  return uploadAndStore(formData)
}
const uploadAndStore = (formData) => {
  const endpoint = '/openai/uploadAndStore'
  return FileUploadService.asyncUpload(endpoint, formData).then((response) => response.data);
}


const onTemplatedUpload = () => {
  toast.add({severity: "info", summary: "Success", detail: "File Uploaded", life: 3000});
};

const formatSize = (bytes) => {
  const k = 1024;
  const dm = 3;
  const sizes = $primevue.config.locale.fileSizeTypes;

  if (bytes === 0) {
    return `0 ${sizes[0]}`;
  }

  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const formattedSize = parseFloat((bytes / Math.pow(k, i)).toFixed(dm));

  return `${formattedSize} ${sizes[i]}`;
};

const removeFromStore = (file) => {

}

const showManageFiles = () => {
  manageFiles.value = !manageFiles.value
  if (isStoreEmpty.value) {
    showFileUpload.value = true
  }
}

const statusSeverity = (file) => {
  if (file.status === 'in_progress') {
    return 'warning'
  } else if (file.status === 'failed') {
    return 'danger'
  } else {
    return 'success'
  }
}
</script>

<template>
  <Card class="mb-8">
    <template #content>
      <div class="text-2xl mb-2 flex gap-2 items-center">
        <div class="flex-1 flex gap-1 items-center">
          <i class="fa-solid fa-brain"></i> AI File Knowledge Store
          <skills-spinner v-if="initLoading" :is-loading="true" :size-in-rem="1.5" :is-inline="true"/>
          <Badge :value="`${currentKnowledgeFiles.length} Files`" severity="warn"/>
        </div>
        <SkillsButton v-if="!initLoading" :icon="`fa-solid ${manageFiles ? 'fa-circle-minus': 'fa-circle-plus'}`" :label="manageFiles ? 'Collapse' : 'Manage'" @click="showManageFiles" size="small"></SkillsButton>
      </div>
      <div v-if="!initLoading && manageFiles">
        <!--      <Message v-if="isStoreEmpty" :closable="false">AI Knowledge store is Empty <SkillsButton label="Upload Files" icon="fa-solid fa-file-circle-plus" @click="showFileUpload=true"></SkillsButton></Message>-->
        <div class="mb-2">
          <div v-if="!isStoreEmpty" class="flex flex-wrap gap-4 my-3 ">
            <div v-for="(file) of currentKnowledgeFiles" :key="file.id"
                 class="p-4 rounded-border flex flex-col border border-surface items-center gap-4 w-[18rem] h-[13rem]">
            <span class="font-semibold text-ellipsis max-w-60 whitespace-nowrap overflow-hidden">{{
                file.filename
              }}</span>
              <div>{{ formatSize(file.bytes) }}</div>
              <Badge :value="file.status" class="mt-4" :severity="statusSeverity(file)"/>
              <Button icon="fa-solid fa-eraser" @click="removeFromStore(file)" variant="outlined" rounded
                      severity="danger"/>
            </div>
            <Button v-if="!showFileUpload"
                    class="p-8 rounded-border flex flex-col justify-center border border-surface items-center gap-4 w-[18rem] h-[13rem]"
                    outlined @click="showFileUpload=true">
              <span class="text-5xl"> <i class="fa-solid fa-file-circle-plus"></i> </span>
              <div>Add More Files</div>
            </Button>
          </div>

          <!--        <SkillsButton v-if="!showFileUpload" label="Upload More Files" icon="fa-solid fa-file-circle-plus" @click="showFileUpload=true"></SkillsButton>-->
        </div>
        <FileUpload v-if="showFileUpload" name="demo[]" :multiple="true"
                    accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.json,.xml,.rtf,.odt,.ods,.odp,.odg,.odc,.odf,.odb,.tex,.md"
                    @select="onSelectedFiles">
          <template #header="{ chooseCallback, uploadCallback }">
            <!--      <pre>{{ files }}</pre>-->
            <div class="flex flex-wrap justify-between items-center flex-1 gap-4">
              <div class="flex gap-2">
                <SkillsButton @click="chooseCallback()" icon="fa-solid fa-file-circle-plus" rounded variant="outlined"
                              severity="secondary" label="Add"></SkillsButton>
                <SkillsButton @click="uploadEvent(uploadCallback)" icon="fa-solid fa-upload" rounded variant="outlined"
                              severity="success" :disabled="!files || files.length === 0" label="Upload"></SkillsButton>
                <!--          <SkillsButton @click="clearCallback()" icon="fa-solid fa-xmark" rounded variant="outlined" severity="danger" :disabled="!files || files.length === 0" label="Clear"></SkillsButton>-->
              </div>
              <ProgressBar :value="totalSizePercent" :showValue="false" class="md:w-20rem h-1 w-full md:ml-auto">
                <span class="whitespace-nowrap">{{ totalSize }}B / 1Mb</span>
              </ProgressBar>
            </div>
          </template>
          <template #content="{ removeFileCallback }">
            <div class="flex flex-col gap-8 pt-4">
              <div v-if="files.length > 0">
                <h5 class="text-xl">Pending</h5>
                <div class="flex flex-wrap gap-4">
                  <div v-for="(file, index) of files" :key="file.name + file.type + file.size"
                       class="p-8 rounded-border flex flex-col border border-surface items-center gap-4">
                <span class="font-semibold text-ellipsis max-w-60 whitespace-nowrap overflow-hidden">{{
                    file.name
                  }}</span>
                    <div>{{ formatSize(file.size) }}</div>
                    <Badge value="Pending" severity="warn"/>
                    <Button icon="fa-solid fa-eraser" @click="onRemoveTemplatingFile(file, removeFileCallback, index)"
                            variant="outlined" rounded severity="danger"/>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <template #empty>
            <div class="flex items-center justify-center flex-col">
              <i class="fa-solid fa-cloud-arrow-up !border-2 !rounded-full !p-8 !text-4xl !text-muted-color"/>
              <p class="mt-6 mb-0">Drag and drop files to here to upload.</p>
            </div>
          </template>
        </FileUpload>
      </div>
    </template>
  </Card>
</template>

<style scoped>

</style>