export type UploadProcessingSceneState = 'idle' | 'uploading' | 'processing'

const CARD_POSITIONS = [
  { x: 523, y: 191 },
  { x: 609, y: 221 },
  { x: 691, y: 236 },
  { x: 770, y: 235 },
]

const CARD_DELAYS = ['0s', '0.75s', '1.5s', '2.25s']

/**
 * Fiz o banner da tela de upload: filmstrip (entrada) → console de processamento
 * (engrenagens + leituras "holográficas") → esteira de frames extraídos →
 * caixa de saída, com um drone flutuando e um horizonte de skyline ao fundo
 * — estilo 3D glossy/cyberpunk da marca (rosa/preto/branco), animado
 * conforme o estado real do upload/processamento.
 */
export function UploadProcessingScene({
  state,
  className,
}: {
  state: UploadProcessingSceneState
  className?: string
}) {
  const uploading = state === 'uploading'
  const processing = state === 'processing'

  return (
    <svg viewBox="0 0 880 320" role="img" aria-labelledby="ups-title" className={className}>
      <title id="ups-title">
        {processing
          ? 'Vídeo sendo processado: frames sendo extraídos'
          : uploading
            ? 'Enviando vídeo para processamento'
            : 'Pipeline de processamento de vídeo: entrada, extração de frames e saída'}
      </title>
      <defs>
        <linearGradient id="ups-badge" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#ff6fa8" />
          <stop offset="55%" stopColor="var(--primary)" />
          <stop offset="100%" stopColor="#5c0a2c" />
        </linearGradient>
        <linearGradient id="ups-card" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.4" />
          <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.1" />
        </linearGradient>
        <linearGradient id="ups-sweep" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="white" stopOpacity="0" />
          <stop offset="50%" stopColor="white" stopOpacity="0.5" />
          <stop offset="100%" stopColor="white" stopOpacity="0" />
        </linearGradient>
        <filter id="ups-blur-sm" x="-60%" y="-60%" width="220%" height="220%">
          <feGaussianBlur stdDeviation="4" />
        </filter>
        <filter id="ups-blur-lg" x="-100%" y="-100%" width="300%" height="300%">
          <feGaussianBlur stdDeviation="20" />
        </filter>
        <clipPath id="ups-console-clip">
          <rect x="250" y="110" width="220" height="110" rx="20" />
        </clipPath>
      </defs>

      {/* Skyline ao fundo, atrás de uma "janela" para o lab cyberpunk */}
      <g opacity="0.5">
        <rect x="0" y="0" width="880" height="320" fill="var(--card)" />
        <g fill="var(--foreground)" opacity="0.08">
          <rect x="20" y="240" width="50" height="80" />
          <rect x="80" y="200" width="36" height="120" />
          <rect x="126" y="230" width="44" height="90" />
          <rect x="700" y="190" width="40" height="130" />
          <rect x="748" y="220" width="34" height="100" />
          <rect x="790" y="205" width="46" height="115" />
        </g>
        <g fill="var(--primary)" opacity="0.4">
          <rect x="30" y="256" width="4" height="4" />
          <rect x="42" y="270" width="4" height="4" />
          <rect x="92" y="216" width="4" height="4" />
          <rect x="712" y="206" width="4" height="4" />
          <rect x="758" y="236" width="4" height="4" />
          <rect x="802" y="222" width="4" height="4" />
        </g>
        {/* Chuva */}
        <g stroke="var(--foreground)" strokeWidth="1.5" strokeLinecap="round">
          {[60, 110, 730, 770, 810].map((x, i) => (
            <line
              key={x}
              x1={x}
              y1="180"
              x2={x - 6}
              y2="210"
              style={{
                animation: `ups-rain 1.6s linear infinite`,
                animationDelay: `${(i * 0.3).toString()}s`,
              }}
            />
          ))}
        </g>
      </g>

      {/* Atmosfera geral em rosa */}
      <ellipse
        cx="440"
        cy="170"
        rx="360"
        ry="150"
        fill="var(--primary)"
        opacity="0.12"
        filter="url(#ups-blur-lg)"
      />

      {/* Filmstrip — entrada de vídeo */}
      <g>
        <ellipse
          cx="90"
          cy="165"
          rx="46"
          ry="80"
          fill="var(--primary)"
          filter="url(#ups-blur-lg)"
          style={{
            animation: `ups-pulse ${uploading ? '0.9s' : '3.2s'} ease-in-out infinite`,
          }}
          opacity="0.5"
        />
        <rect x="64" y="95" width="52" height="140" rx="10" fill="url(#ups-badge)" />
        <rect
          x="64"
          y="95"
          width="52"
          height="140"
          rx="10"
          fill="none"
          stroke="var(--primary)"
          strokeOpacity="0.8"
          strokeWidth="1.5"
          filter="url(#ups-blur-sm)"
        />
        <g fill="var(--background)" fillOpacity="0.55">
          <rect x="72" y="106" width="8" height="8" rx="2" />
          <rect x="72" y="126" width="8" height="8" rx="2" />
          <rect x="72" y="146" width="8" height="8" rx="2" />
          <rect x="72" y="166" width="8" height="8" rx="2" />
          <rect x="72" y="186" width="8" height="8" rx="2" />
          <rect x="72" y="206" width="8" height="8" rx="2" />
        </g>
        <path d="M92 152 L112 165 L92 178 Z" fill="white" />
      </g>
      <text
        x="90"
        y="248"
        textAnchor="middle"
        fontFamily="var(--font-heading)"
        fontSize="11"
        fill="var(--muted-foreground)"
      >
        Vídeo
      </text>

      {/* Feixe conectando a entrada ao console */}
      <line
        x1="118"
        y1="165"
        x2="248"
        y2="165"
        stroke="var(--primary)"
        strokeOpacity="0.5"
        strokeWidth="2"
        strokeDasharray="6 6"
        filter="url(#ups-blur-sm)"
      />

      {/* Console de processamento */}
      <g>
        <rect x="254" y="114" width="220" height="110" rx="20" fill="#170208" />
        <rect x="250" y="110" width="220" height="110" rx="20" fill="url(#ups-badge)" />
        <rect
          x="250"
          y="110"
          width="220"
          height="110"
          rx="20"
          fill="none"
          stroke="var(--primary)"
          strokeOpacity="0.8"
          strokeWidth="1.5"
          filter="url(#ups-blur-sm)"
        />
        {uploading && (
          <g clipPath="url(#ups-console-clip)">
            <rect
              x="250"
              y="110"
              width="20"
              height="110"
              fill="url(#ups-sweep)"
              style={{ animation: 'ups-scan-sweep 1.4s linear infinite' }}
            />
          </g>
        )}

        {/* Engrenagens */}
        <g
          style={{
            transformOrigin: '300px 165px',
            animation: processing ? 'ups-gear-spin 3s linear infinite' : undefined,
          }}
        >
          <Gear cx={300} cy={165} r={20} teeth={8} />
        </g>
        <g
          style={{
            transformOrigin: '350px 175px',
            animation: processing ? 'ups-gear-spin-reverse 2.3s linear infinite' : undefined,
          }}
        >
          <Gear cx={350} cy={175} r={14} teeth={6} />
        </g>
      </g>

      {/* Leituras holográficas */}
      <g fontFamily="var(--font-heading)" fontSize="11" fontWeight={600}>
        <rect
          x="392"
          y="70"
          width="70"
          height="26"
          rx="6"
          fill="var(--background)"
          fillOpacity="0.6"
          stroke="var(--primary)"
          strokeOpacity="0.6"
        />
        <text x="427" y="87" textAnchor="middle" fill="var(--primary)">
          FPS: 60
        </text>
        <rect
          x="256"
          y="70"
          width="112"
          height="26"
          rx="6"
          fill="var(--background)"
          fillOpacity="0.6"
          stroke="var(--primary)"
          strokeOpacity="0.6"
          style={{ animation: processing ? 'ups-pulse 1.4s ease-in-out infinite' : undefined }}
        />
        <text x="312" y="87" textAnchor="middle" fill="var(--primary)">
          {processing ? 'Processando…' : uploading ? 'Enviando…' : 'Em espera'}
        </text>
      </g>

      {/* Esteira com os frames extraídos (PNG) */}
      <path
        d="M470 165 Q650 260 800 230"
        fill="none"
        stroke="var(--foreground)"
        strokeOpacity="0.15"
        strokeWidth="2"
        strokeDasharray="4 8"
      />
      {CARD_POSITIONS.map((pos, i) => (
        <g
          key={`${pos.x.toString()}-${pos.y.toString()}`}
          transform={`translate(${(pos.x - 8).toString()} ${(pos.y - 8).toString()}) rotate(${(i % 2 === 0 ? -8 : 8).toString()} 8 8)`}
          style={
            processing
              ? {
                  animation: 'ups-card-flow 3s linear infinite',
                  animationDelay: CARD_DELAYS[i],
                }
              : undefined
          }
        >
          <rect
            width="16"
            height="16"
            rx="4"
            fill="url(#ups-card)"
            stroke="var(--foreground)"
            strokeOpacity="0.2"
          />
          <path d="M6 5 L11 8 L6 11 Z" fill="var(--foreground)" fillOpacity="0.85" />
        </g>
      ))}

      {/* Caixa de saída */}
      <g>
        <path
          d="M770 218 L830 218 L822 250 L778 250 Z"
          fill="#170208"
          stroke="var(--primary)"
          strokeOpacity="0.7"
          strokeWidth="1.5"
        />
        <rect x="778" y="210" width="44" height="10" rx="3" fill="url(#ups-badge)" />
      </g>
      <text
        x="800"
        y="268"
        textAnchor="middle"
        fontFamily="var(--font-heading)"
        fontSize="11"
        fill="var(--muted-foreground)"
      >
        PNG frames
      </text>

      {/* Drone flutuando */}
      <g
        style={{
          transformOrigin: '560px 100px',
          animation: 'ups-bob 2.6s ease-in-out infinite',
        }}
      >
        <line
          x1="548"
          y1="96"
          x2="536"
          y2="90"
          stroke="var(--foreground)"
          strokeOpacity="0.5"
          strokeWidth="1.5"
        />
        <line
          x1="572"
          y1="96"
          x2="584"
          y2="90"
          stroke="var(--foreground)"
          strokeOpacity="0.5"
          strokeWidth="1.5"
        />
        <circle
          cx="536"
          cy="90"
          r="5"
          fill="none"
          stroke="var(--foreground)"
          strokeOpacity="0.5"
          strokeWidth="1.5"
        />
        <circle
          cx="584"
          cy="90"
          r="5"
          fill="none"
          stroke="var(--foreground)"
          strokeOpacity="0.5"
          strokeWidth="1.5"
        />
        <rect x="544" y="92" width="32" height="18" rx="9" fill="url(#ups-badge)" />
        <circle cx="560" cy="101" r="3" fill="white" />
      </g>
    </svg>
  )
}

function Gear({ cx, cy, r, teeth }: { cx: number; cy: number; r: number; teeth: number }) {
  const toothWidth = r * 0.34
  const toothLength = r * 0.4
  return (
    <g>
      <circle
        cx={cx}
        cy={cy}
        r={r}
        fill="#170208"
        stroke="var(--primary)"
        strokeOpacity="0.7"
        strokeWidth="2"
      />
      {Array.from({ length: teeth }, (_, i) => {
        const angle = (360 / teeth) * i
        return (
          <rect
            key={angle}
            x={cx - toothWidth / 2}
            y={cy - r - toothLength}
            width={toothWidth}
            height={toothLength}
            rx="1.5"
            fill="var(--primary)"
            fillOpacity="0.7"
            transform={`rotate(${angle.toString()} ${cx.toString()} ${cy.toString()})`}
          />
        )
      })}
      <circle cx={cx} cy={cy} r={r * 0.35} fill="var(--primary)" fillOpacity="0.5" />
    </g>
  )
}
