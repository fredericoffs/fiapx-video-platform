import type { ReactNode } from 'react'
import { Download, Loader2, UploadCloud } from 'lucide-react'
import { VideoProcessingIllustration } from '@/shared/ui/illustrations/video-processing-illustration'

const FEATURES = [
  {
    icon: UploadCloud,
    title: 'Upload rápido',
    description: 'Arraste um vídeo e acompanhe o envio com uma barra de progresso real.',
  },
  {
    icon: Loader2,
    title: 'Processamento assíncrono',
    description: 'A extração dos frames roda em segundo plano — não trava sua tela.',
  },
  {
    icon: Download,
    title: 'Download em um clique',
    description: 'Baixe um .zip com os frames extraídos assim que o processamento terminar.',
  },
]

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto grid min-h-[70vh] max-w-4xl grid-cols-1 items-center gap-8 md:grid-cols-2">
      <div className="hidden flex-col items-center gap-8 md:flex">
        <VideoProcessingIllustration className="mx-auto w-full max-w-sm" />
        <div className="max-w-sm text-center">
          <h1 className="font-heading text-2xl font-bold tracking-tight">
            Vídeo enviado, frames prontos
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            A fiapx.video extrai um frame por segundo de cada vídeo enviado — envie, acompanhe o
            status em tempo real e baixe o resultado quando terminar.
          </p>
        </div>
        <ul className="flex w-full max-w-sm flex-col gap-4">
          {FEATURES.map((feature) => (
            <li key={feature.title} className="flex items-start gap-3">
              <feature.icon className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden />
              <div>
                <p className="text-sm font-medium">{feature.title}</p>
                <p className="text-sm text-muted-foreground">{feature.description}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
      {children}
    </div>
  )
}
