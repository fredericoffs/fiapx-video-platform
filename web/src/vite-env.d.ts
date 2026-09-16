/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

interface Window {
  __ENV__?: { API_BASE_URL?: string }
  /** File System Access API — só existe em navegadores Chromium; ver download-video.ts.
   * Nome próprio (não FileSystemFileHandle) pra não colidir por merge de declaração com a
   * interface homônima, mais restrita, já embutida no lib.dom.d.ts do TypeScript. */
  showSaveFilePicker?: (options?: { suggestedName?: string }) => Promise<SaveFileHandle>
}

interface SaveFileHandle {
  createWritable: () => Promise<WritableStream<Uint8Array>>
}
