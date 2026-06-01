type MessageHandler = (body: any) => void

function encodeFrame(command: string, headers: Record<string, string> = {}, body = '') {
  const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`)
  return `${command}\n${headerLines.join('\n')}\n\n${body}\0`
}

function parseFrames(data: string) {
  return data
    .split('\0')
    .map((frame) => frame.trim())
    .filter(Boolean)
}

export function subscribePool(poolId: number, onMessage: MessageHandler) {
  const token = localStorage.getItem('accessToken')
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const socket = new WebSocket(`${protocol}://${window.location.host}/ws`)
  let connected = false

  socket.addEventListener('open', () => {
    socket.send(encodeFrame('CONNECT', {
      'accept-version': '1.2',
      host: window.location.host,
      Authorization: token ? `Bearer ${token}` : '',
    }))
  })

  socket.addEventListener('message', (event) => {
    for (const frame of parseFrames(String(event.data))) {
      if (frame.startsWith('CONNECTED')) {
        connected = true
        socket.send(encodeFrame('SUBSCRIBE', {
          id: `pool-${poolId}`,
          destination: `/topic/pool/${poolId}`,
        }))
        continue
      }
      if (!frame.startsWith('MESSAGE')) continue
      const bodyStart = frame.indexOf('\n\n')
      if (bodyStart < 0) continue
      const rawBody = frame.slice(bodyStart + 2)
      try {
        onMessage(JSON.parse(rawBody))
      } catch {
        onMessage(rawBody)
      }
    }
  })

  const heartbeat = window.setInterval(() => {
    if (connected && socket.readyState === WebSocket.OPEN) {
      socket.send('\n')
    }
  }, 10000)

  return () => {
    window.clearInterval(heartbeat)
    if (socket.readyState === WebSocket.OPEN) {
      socket.send(encodeFrame('DISCONNECT'))
      socket.close()
    }
  }
}
