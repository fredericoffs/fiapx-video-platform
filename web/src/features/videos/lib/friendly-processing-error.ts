/**
 * O `errorMessage` de um vídeo FAILED vem cru do video-worker (mensagem de exceção Java,
 * às vezes com saída do ffmpeg/ffprobe e caminho de arquivo temporário embutidos — ver
 * FfmpegFrameExtractor). Aqui eu traduzo os padrões conhecidos pra uma frase que faz
 * sentido pra quem está usando o site; o texto técnico completo fica disponível à parte
 * (ver `<details>` em VideoList) pra quem quiser depurar.
 */
export function friendlyProcessingError(errorMessage: string | null): string {
  if (!errorMessage) {
    return 'Não foi possível processar o vídeo.'
  }

  if (/excede o limite de \d+s? permitido/i.test(errorMessage)) {
    return 'O vídeo é mais longo do que o permitido nesta infraestrutura.'
  }

  if (/^timeout/i.test(errorMessage) || /timeout de processamento/i.test(errorMessage)) {
    return 'O processamento demorou demais e foi interrompido. Tente um vídeo menor.'
  }

  if (
    /moov atom not found/i.test(errorMessage) ||
    /invalid data found when processing input/i.test(errorMessage) ||
    /nenhum frame extraído/i.test(errorMessage)
  ) {
    return 'O arquivo parece corrompido ou não é um vídeo válido.'
  }

  if (/ffprobe saiu com código|ffmpeg saiu com código/i.test(errorMessage)) {
    return 'Não foi possível ler o conteúdo do vídeo — verifique se o arquivo não está corrompido.'
  }

  return 'Ocorreu um erro inesperado ao processar o vídeo.'
}
