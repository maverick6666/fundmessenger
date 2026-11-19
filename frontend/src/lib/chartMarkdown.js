const INLINE_CHART_REGEX = /`(chart(?:-[\w-]+)?)\s+({[\s\S]*?})\s*`/g
const EMPTY_FENCE_REGEX = /(?:^|\n)```[ \t]*\n(?:[ \t]*\n)*```(?=\n|$)/g
const ORPHAN_BACKTICK_LINE_REGEX = /(^|\n)[ \t]*(?:`{1,2}|`{4,})[ \t]*(?=\n|$)/g
const CHART_WRAPPER_OPEN_REGEX = /(^|\n)[ \t]*`{1,3}[ \t]*\n(?=```chart(?:-[\w-]+)?\s*\n)/g
const CHART_WRAPPER_CLOSE_REGEX = /(```chart(?:-[\w-]+)?\s*\n[\s\S]*?\n```)\n[ \t]*`{1,3}[ \t]*(?=\n|$)/g

export function normalizeChartMarkdown(markdown) {
  if (!markdown) return ''

  const normalized = markdown
    .replace(/\r\n/g, '\n')
    .replace(INLINE_CHART_REGEX, (_, language, json) => {
      return `\n\n\`\`\`${language}\n${json.trim()}\n\`\`\`\n\n`
    })

  return normalized
    .replace(CHART_WRAPPER_OPEN_REGEX, '$1')
    .replace(CHART_WRAPPER_CLOSE_REGEX, '$1')
    .replace(EMPTY_FENCE_REGEX, '\n\n')
    .replace(ORPHAN_BACKTICK_LINE_REGEX, '\n')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}
