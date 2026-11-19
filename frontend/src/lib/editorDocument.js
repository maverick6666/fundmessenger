export function parseEditorDocument(contentJson, fallbackContent = '') {
  if (contentJson) {
    try {
      return JSON.parse(contentJson)
    } catch {
      // Fall back to markdown/string content when legacy JSON is malformed.
    }
  }

  return fallbackContent || ''
}

export function serializeEditorDocument(editor) {
  if (!editor) return null

  try {
    return JSON.stringify(editor.getJSON())
  } catch {
    return null
  }
}
