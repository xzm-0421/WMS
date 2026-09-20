/**
 * 生成《WMS PDA 操作手册》docx（含附图）
 *
 * 内容来源：WMS-PDA操作手册.md
 * 图片：Markdown 中 ![说明](pda-manual-images/xxx.png)
 *
 * 运行：node generate-pda-manual.mjs
 */
import {
  Document,
  Packer,
  Paragraph,
  TextRun,
  HeadingLevel,
  AlignmentType,
  Table,
  TableRow,
  TableCell,
  WidthType,
  BorderStyle,
  PageNumber,
  Header,
  Footer,
  LevelFormat,
  ImageRun,
} from 'docx'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SRC = path.join(__dirname, 'WMS-PDA操作手册.md')
const OUT = path.join(__dirname, 'WMS-PDA操作手册.docx')

const FONT = '微软雅黑'
const TABLE_WIDTH = 9000
/** 手机竖屏示意图在 Word 中的显示宽度（像素） */
const IMG_DISPLAY_WIDTH = 260

const thin = { style: BorderStyle.SINGLE, size: 4, color: 'CBD5E1' }
const borders = { top: thin, bottom: thin, left: thin, right: thin }
const headerShading = { fill: '1E40AF' }
const altShading = { fill: 'F1F5F9' }

function inlineRuns(text, base = {}) {
  const size = base.size || 21
  const runs = []
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g
  let last = 0
  let match
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) {
      runs.push(new TextRun({ text: text.slice(last, match.index), font: FONT, size, ...base }))
    }
    const token = match[0]
    if (token.startsWith('**')) {
      runs.push(new TextRun({ text: token.slice(2, -2), font: FONT, size, ...base, bold: true }))
    } else {
      runs.push(
        new TextRun({
          text: token.slice(1, -1),
          font: 'Consolas',
          size: size - 2,
          color: base.color || 'B45309',
        }),
      )
    }
    last = match.index + token.length
  }
  if (last < text.length) {
    runs.push(new TextRun({ text: text.slice(last), font: FONT, size, ...base }))
  }
  return runs.length ? runs : [new TextRun({ text: '', font: FONT, size })]
}

function p(text) {
  return new Paragraph({ spacing: { after: 120, line: 360 }, children: inlineRuns(text) })
}

function heading(text, level) {
  const preset = {
    1: { heading: HeadingLevel.HEADING_1, size: 32, color: '1E3A8A', before: 360, after: 200 },
    2: { heading: HeadingLevel.HEADING_2, size: 26, color: '1E40AF', before: 280, after: 140 },
    3: { heading: HeadingLevel.HEADING_3, size: 22, color: '334155', before: 200, after: 100 },
  }[level]
  return new Paragraph({
    heading: preset.heading,
    spacing: { before: preset.before, after: preset.after },
    children: [new TextRun({ text, font: FONT, size: preset.size, bold: true, color: preset.color })],
  })
}

function bullet(text) {
  return new Paragraph({
    numbering: { reference: 'bullets', level: 0 },
    spacing: { after: 80, line: 340 },
    children: inlineRuns(text),
  })
}

function step(text, reference) {
  return new Paragraph({
    numbering: { reference, level: 0 },
    spacing: { after: 80, line: 340 },
    children: inlineRuns(text),
  })
}

function note(text) {
  return new Paragraph({
    spacing: { after: 140, before: 60, line: 340 },
    border: { left: { style: BorderStyle.SINGLE, size: 24, color: 'F59E0B' } },
    indent: { left: 120 },
    children: [
      new TextRun({ text: '提示：', font: FONT, size: 20, bold: true, color: 'B45309' }),
      ...inlineRuns(text, { size: 20, color: '78350F' }),
    ],
  })
}

function quote(text) {
  return new Paragraph({
    spacing: { after: 140, before: 60, line: 340 },
    border: { left: { style: BorderStyle.SINGLE, size: 24, color: '94A3B8' } },
    indent: { left: 120 },
    children: inlineRuns(text, { size: 20, color: '475569' }),
  })
}

function cell(text, opts = {}) {
  const isHeader = !!opts.header
  return new TableCell({
    borders,
    width: { size: opts.width || 2400, type: WidthType.DXA },
    shading: isHeader ? headerShading : opts.alt ? altShading : undefined,
    children: [
      new Paragraph({
        spacing: { after: 40, before: 40 },
        children: isHeader
          ? [new TextRun({ text, font: FONT, size: 18, bold: true, color: 'FFFFFF' })]
          : inlineRuns(text, { size: 17, color: '0F172A' }),
      }),
    ],
  })
}

function estimateWidths(headers, rows) {
  const weights = headers.map((h, i) => {
    const lengths = [h, ...rows.map((r) => r[i] ?? '')].map((v) => String(v).length)
    return Math.max(4, Math.min(40, Math.max(...lengths)))
  })
  const total = weights.reduce((a, b) => a + b, 0)
  return weights.map((w) => Math.floor((w / total) * TABLE_WIDTH))
}

function table(headers, rows) {
  const widths = estimateWidths(headers, rows)
  return new Table({
    width: { size: TABLE_WIDTH, type: WidthType.DXA },
    columnWidths: widths,
    rows: [
      new TableRow({
        tableHeader: true,
        children: headers.map((h, i) => cell(h, { header: true, width: widths[i] })),
      }),
      ...rows.map(
        (r, ri) =>
          new TableRow({
            children: headers.map((_, i) => cell(String(r[i] ?? ''), { width: widths[i], alt: ri % 2 === 1 })),
          }),
      ),
    ],
  })
}

function splitRow(line) {
  return line
    .replace(/^\s*\|/, '')
    .replace(/\|\s*$/, '')
    .split('|')
    .map((c) => c.trim())
}

const isTableRow = (line) => /^\s*\|.*\|\s*$/.test(line)
const isTableDivider = (line) => /^\s*\|[\s:|-]+\|\s*$/.test(line)

/** 读取 PNG IHDR 宽高 */
function readPngSize(buf) {
  if (buf.length < 24 || buf.toString('ascii', 1, 4) !== 'PNG') {
    return null
  }
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) }
}

function figureParagraphs(caption, relPath) {
  const abs = path.resolve(__dirname, relPath)
  if (!fs.existsSync(abs)) {
    console.warn(`[warn] 图片不存在: ${abs}`)
    return [
      note(`附图缺失：${caption}（${relPath}）`),
    ]
  }
  const data = fs.readFileSync(abs)
  const size = readPngSize(data)
  const aspect = size ? size.height / size.width : 16 / 9
  const width = IMG_DISPLAY_WIDTH
  const height = Math.round(width * aspect)
  const name = path.basename(relPath)
  return [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 160, after: 60 },
      children: [
        new ImageRun({
          type: 'png',
          data,
          transformation: { width, height },
          altText: { title: caption, description: caption, name },
        }),
      ],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 200 },
      children: [
        new TextRun({ text: caption, font: FONT, size: 18, color: '475569', italics: true }),
      ],
    }),
  ]
}

function parseMarkdown(md) {
  const lines = md.replace(/\r\n/g, '\n').split('\n')
  const children = []
  const orderedRefs = []
  const cover = { title: '', lines: [] }
  let coverDone = false
  let i = 0
  let imageCount = 0

  while (i < lines.length) {
    const line = lines[i]
    const trimmed = line.trim()

    if (!trimmed || trimmed === '---') {
      i += 1
      continue
    }

    if (!coverDone && trimmed.startsWith('# ')) {
      cover.title = trimmed.slice(2).trim()
      i += 1
      while (i < lines.length && !lines[i].trim()) i += 1
      while (i < lines.length && lines[i].trim().startsWith('>')) {
        cover.lines.push(lines[i].trim().replace(/^>\s?/, ''))
        i += 1
      }
      coverDone = true
      continue
    }

    if (trimmed.startsWith('#### ')) {
      children.push(heading(trimmed.slice(5).trim(), 3))
      i += 1
      continue
    }
    if (trimmed.startsWith('### ')) {
      children.push(heading(trimmed.slice(4).trim(), 2))
      i += 1
      continue
    }
    if (trimmed.startsWith('## ')) {
      children.push(heading(trimmed.slice(3).trim(), 1))
      i += 1
      continue
    }

    const imgMatch = /^!\[([^\]]*)\]\(([^)]+)\)\s*$/.exec(trimmed)
    if (imgMatch) {
      imageCount += 1
      children.push(...figureParagraphs(imgMatch[1] || `附图${imageCount}`, imgMatch[2].trim()))
      i += 1
      continue
    }

    if (trimmed.startsWith('>')) {
      const text = trimmed.replace(/^>\s?/, '')
      children.push(text.startsWith('提示：') ? note(text.slice(3)) : quote(text))
      i += 1
      continue
    }

    if (isTableRow(trimmed) && isTableDivider(lines[i + 1] || '')) {
      const headers = splitRow(trimmed)
      i += 2
      const rows = []
      while (i < lines.length && isTableRow(lines[i].trim())) {
        rows.push(splitRow(lines[i].trim()))
        i += 1
      }
      children.push(table(headers, rows))
      children.push(new Paragraph({ spacing: { after: 120 }, children: [] }))
      continue
    }

    if (/^[-*]\s+/.test(trimmed)) {
      while (i < lines.length && /^[-*]\s+/.test(lines[i].trim())) {
        children.push(bullet(lines[i].trim().replace(/^[-*]\s+/, '')))
        i += 1
      }
      continue
    }

    const orderedMatch = /^(\d+)\.\s+/.exec(trimmed)
    if (orderedMatch) {
      const reference = `steps-${orderedRefs.length}`
      orderedRefs.push({ reference, start: Number(orderedMatch[1]) })
      while (i < lines.length && /^\d+\.\s+/.test(lines[i].trim())) {
        children.push(step(lines[i].trim().replace(/^\d+\.\s+/, ''), reference))
        i += 1
      }
      continue
    }

    children.push(p(trimmed))
    i += 1
  }

  return { cover, children, orderedRefs, imageCount }
}

function coverParagraphs(cover) {
  const centered = (text, opts) =>
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: opts.after ?? 120 },
      children: [
        new TextRun({
          text,
          font: FONT,
          size: opts.size,
          bold: opts.bold,
          color: opts.color,
        }),
      ],
    })

  return [
    new Paragraph({ spacing: { before: 1200 }, children: [] }),
    centered('WMS 仓储管理系统', { size: 44, bold: true, color: '1E3A8A', after: 160 }),
    centered('PDA 操作手册', { size: 40, bold: true, color: '1E40AF', after: 600 }),
    ...cover.lines.map((text) =>
      centered(text.replace(/\*\*/g, ''), { size: 22, color: '475569', after: 120 }),
    ),
    centered(`编制日期：${new Date().toISOString().slice(0, 10)}`, {
      size: 22,
      color: '475569',
      after: 1400,
    }),
    centered('附图为界面示意图，实际以设备显示为准。', {
      size: 18,
      color: '94A3B8',
      after: 80,
    }),
    centered('内容源文件：docs/WMS-PDA操作手册.md', {
      size: 18,
      color: '94A3B8',
      after: 0,
    }),
  ]
}

const markdown = fs.readFileSync(SRC, 'utf8')
const { cover, children, orderedRefs, imageCount } = parseMarkdown(markdown)

const doc = new Document({
  styles: {
    default: {
      document: { styles: [{ id: 'Normal', name: 'Normal', run: { font: FONT, size: 21 } }] },
    },
  },
  numbering: {
    config: [
      {
        reference: 'bullets',
        levels: [
          {
            level: 0,
            format: LevelFormat.BULLET,
            text: '•',
            alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 420, hanging: 240 } } },
          },
        ],
      },
      ...orderedRefs.map(({ reference, start }) => ({
        reference,
        levels: [
          {
            level: 0,
            format: LevelFormat.DECIMAL,
            text: '%1.',
            alignment: AlignmentType.LEFT,
            start,
            style: { paragraph: { indent: { left: 420, hanging: 240 } } },
          },
        ],
      })),
    ],
  },
  sections: [
    {
      properties: { page: { margin: { top: 720, bottom: 720, left: 720, right: 720 } } },
      headers: {
        default: new Header({
          children: [
            new Paragraph({
              alignment: AlignmentType.RIGHT,
              children: [
                new TextRun({ text: 'WMS PDA 操作手册', font: FONT, size: 16, color: '64748B' }),
              ],
            }),
          ],
        }),
      },
      footers: {
        default: new Footer({
          children: [
            new Paragraph({
              alignment: AlignmentType.CENTER,
              children: [
                new TextRun({ text: '第 ', font: FONT, size: 16, color: '64748B' }),
                new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 16, color: '64748B' }),
                new TextRun({ text: ' 页', font: FONT, size: 16, color: '64748B' }),
              ],
            }),
          ],
        }),
      },
      children: [...coverParagraphs(cover), ...children],
    },
  ],
})

const buf = await Packer.toBuffer(doc)
fs.writeFileSync(OUT, buf)
console.log(`已生成: ${OUT}`)
console.log(`段落块: ${children.length}，附图: ${imageCount}，有序列表块: ${orderedRefs.length}`)
