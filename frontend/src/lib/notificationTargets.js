export function resolveNotificationTarget(rawUrl) {
  if (!rawUrl || typeof rawUrl !== 'string') {
    return { kind: 'none' }
  }

  const chatMatch = rawUrl.match(/^\/club\/chat\/(\d+)$/)
  if (chatMatch) {
    return { kind: 'chat', roomId: Number(chatMatch[1]) }
  }

  const boardPostMatch = rawUrl.match(/^\/club\/boards\/posts\/(\d+)$/)
  if (boardPostMatch) {
    return { kind: 'route', url: `/club/boards?post=${boardPostMatch[1]}` }
  }

  const driveFileMatch = rawUrl.match(/^\/club\/drive\/(\d+)$/)
  if (driveFileMatch) {
    return { kind: 'route', url: `/club/drive?file=${driveFileMatch[1]}` }
  }

  const stockMatch = rawUrl.match(/^\/club\/jongtobang\/(\d+)$/)
  if (stockMatch) {
    return { kind: 'route', url: `/club/jongtobang?stockId=${stockMatch[1]}` }
  }

  const reminderMatch = rawUrl.match(/^\/club\?announcement=(\d+)(?:&.*)?$/)
  if (reminderMatch) {
    return { kind: 'route', url: `/club/boards?announcement=${reminderMatch[1]}` }
  }

  return { kind: 'route', url: rawUrl }
}

export function getNotificationRoomId(notification) {
  const target = resolveNotificationTarget(notification?.targetUrl)
  return target.kind === 'chat' ? target.roomId : null
}

export function isChatNotification(notification) {
  if (notification?.type === 'OPEN_CHAT_CREATED') return false
  return resolveNotificationTarget(notification?.targetUrl).kind === 'chat'
}
