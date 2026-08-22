/**
 * Fiz uma cena 3D/glossy (estilo Pixar/Dreamworks) com grade de perspectiva e brilho
 * neon (cyberpunk), na paleta da marca (rosa `--primary` + preto/branco do
 * tema) em vez das cores neon tradicionais — só linhas/preenchimentos
 * temáticos, sem depender de nenhuma imagem externa.
 */
export function VideoProcessingIllustration({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 440 440"
      role="img"
      aria-labelledby="video-processing-illustration-title"
      className={className}
    >
      <title id="video-processing-illustration-title">
        Ilustração de um botão de play processando frames de vídeo
      </title>
      <defs>
        <radialGradient id="vpi-glow" cx="50%" cy="46%" r="55%">
          <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.55" />
          <stop offset="100%" stopColor="var(--primary)" stopOpacity="0" />
        </radialGradient>
        <linearGradient id="vpi-badge" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#ff6fa8" />
          <stop offset="55%" stopColor="var(--primary)" />
          <stop offset="100%" stopColor="#5c0a2c" />
        </linearGradient>
        <linearGradient id="vpi-frame" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.35" />
          <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.08" />
        </linearGradient>
        <filter id="vpi-soft-blur" x="-60%" y="-60%" width="220%" height="220%">
          <feGaussianBlur stdDeviation="6" />
        </filter>
        <filter id="vpi-big-blur" x="-100%" y="-100%" width="300%" height="300%">
          <feGaussianBlur stdDeviation="26" />
        </filter>
      </defs>

      {/* Atmosfera */}
      <circle cx="220" cy="184" r="170" fill="url(#vpi-glow)" filter="url(#vpi-big-blur)" />

      {/* Piso de perspectiva (grade cyberpunk) */}
      <g stroke="var(--foreground)" strokeOpacity="0.12" strokeWidth="1" fill="none">
        <path d="M160 336 L280 336" />
        <path d="M110 366 L330 366" />
        <path d="M60 396 L380 396" />
        <path d="M20 420 L420 420" />
        <path d="M220 300 L20 420" />
        <path d="M220 300 L90 420" />
        <path d="M220 300 L155 420" />
        <path d="M220 300 L220 420" />
        <path d="M220 300 L285 420" />
        <path d="M220 300 L350 420" />
        <path d="M220 300 L420 420" />
      </g>

      {/* Sombra de contato do badge */}
      <ellipse
        cx="222"
        cy="304"
        rx="72"
        ry="12"
        fill="black"
        opacity="0.35"
        filter="url(#vpi-soft-blur)"
      />

      {/* Linhas conectando o badge aos frames extraídos */}
      <g
        stroke="var(--primary)"
        strokeOpacity="0.55"
        strokeWidth="1.5"
        fill="none"
        filter="url(#vpi-soft-blur)"
      >
        <path d="M158 150 Q100 120 74 96" />
        <path d="M284 146 Q330 116 352 92" />
        <path d="M196 262 Q150 300 108 322" />
      </g>

      {/* Partículas / bokeh */}
      <g fill="var(--primary)">
        <circle cx="368" cy="150" r="3" opacity="0.5" />
        <circle cx="52" cy="210" r="2.5" opacity="0.4" />
        <circle cx="330" cy="270" r="2" opacity="0.35" />
        <circle cx="90" cy="120" r="2" opacity="0.4" />
      </g>

      {/* Frame chip A (topo-esquerda) */}
      <g transform="rotate(-9 74 96)">
        <rect
          x="46"
          y="68"
          width="56"
          height="56"
          rx="12"
          fill="url(#vpi-frame)"
          stroke="var(--foreground)"
          strokeOpacity="0.18"
        />
        <path
          d="M58 118 L58 104"
          stroke="var(--foreground)"
          strokeOpacity="0.3"
          strokeWidth="2"
          strokeLinecap="round"
        />
        <circle cx="74" cy="96" r="9" fill="var(--background)" fillOpacity="0.5" />
        <path d="M71 91 L81 96 L71 101 Z" fill="var(--foreground)" fillOpacity="0.85" />
      </g>

      {/* Frame chip B (direita) */}
      <g transform="rotate(11 352 92)">
        <rect
          x="326"
          y="66"
          width="52"
          height="52"
          rx="11"
          fill="url(#vpi-frame)"
          stroke="var(--foreground)"
          strokeOpacity="0.18"
        />
        <path
          d="M336 112 L336 100"
          stroke="var(--foreground)"
          strokeOpacity="0.3"
          strokeWidth="2"
          strokeLinecap="round"
        />
        <circle cx="352" cy="92" r="8" fill="var(--background)" fillOpacity="0.5" />
        <path d="M349 88 L358 92 L349 96 Z" fill="var(--foreground)" fillOpacity="0.85" />
      </g>

      {/* Frame chip C (frente, concluído) */}
      <g transform="rotate(-5 108 322)">
        <rect
          x="76"
          y="292"
          width="64"
          height="64"
          rx="14"
          fill="url(#vpi-frame)"
          stroke="var(--foreground)"
          strokeOpacity="0.2"
        />
        <circle cx="108" cy="322" r="10" fill="var(--background)" fillOpacity="0.5" />
        <path d="M105 317 L115 322 L105 327 Z" fill="var(--foreground)" fillOpacity="0.9" />
        <circle cx="127" cy="303" r="9" fill="var(--primary)" />
        <path
          d="M123 303 L126 306 L131.5 300"
          stroke="var(--primary-foreground)"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />
      </g>

      {/* Anel de "processando", ecoando o badge animado da listagem */}
      <circle
        cx="220"
        cy="188"
        r="104"
        fill="none"
        stroke="var(--primary)"
        strokeOpacity="0.75"
        strokeWidth="3"
        strokeLinecap="round"
        strokeDasharray="360 490"
        filter="url(#vpi-soft-blur)"
      />
      <circle cx="220" cy="84" r="5" fill="var(--primary)" filter="url(#vpi-soft-blur)" />

      {/* Badge — base escura (dá espessura/profundidade ao bloco) */}
      <rect x="160" y="134" width="124" height="124" rx="30" fill="#3a0619" />
      {/* Badge — face principal */}
      <rect x="154" y="126" width="124" height="124" rx="30" fill="url(#vpi-badge)" />
      <rect
        x="154"
        y="126"
        width="124"
        height="124"
        rx="30"
        fill="none"
        stroke="var(--primary)"
        strokeOpacity="0.9"
        strokeWidth="1.5"
        filter="url(#vpi-soft-blur)"
      />
      <ellipse
        cx="192"
        cy="156"
        rx="34"
        ry="18"
        fill="white"
        opacity="0.25"
        filter="url(#vpi-soft-blur)"
      />

      {/* Ícone de play */}
      <path
        d="M200 158 L242 188 L200 218 Z"
        fill="#3a0619"
        opacity="0.5"
        transform="translate(3 3)"
      />
      <path d="M200 158 L242 188 L200 218 Z" fill="white" />
    </svg>
  )
}
