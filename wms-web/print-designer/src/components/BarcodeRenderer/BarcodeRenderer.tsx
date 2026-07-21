/**
 * 条码/二维码渲染器
 *
 * 一维码使用 JsBarcode 渲染 SVG，
 * 二维码使用 QRCode.create() 获取矩阵后直接渲染 SVG <rect> 网格。
 *
 * 关键设计：二维码完全同步渲染（QRCode.create 是同步方法），
 * 不依赖 canvas / dangerouslySetInnerHTML / toDataURL 等异步方式，
 * 避免浏览器环境下的渲染失败问题。
 */
import React, { useMemo, useRef, useEffect } from 'react'
import JsBarcode from 'jsbarcode'
import QRCode from 'qrcode'

interface BarcodeRendererProps {
  barcodeType: string
  value: string
  isPreview: boolean
  width: number
  height: number
}

/** 格式映射 */
const FORMAT_MAP: Record<string, string> = {
  code128: 'CODE128',
  code39: 'CODE39',
  ean13: 'EAN13',
}

/**
 * 从 QRCode.create() 的矩阵数据生成 SVG <rect> 元素
 * — 同步计算，不依赖 canvas 或异步 API
 */
function buildQrSvgRects(
  qrModules: { size: number; data: Uint8Array | number[] },
  cellPx: number,
  marginPx: number
): React.ReactElement[] {
  const { size, data } = qrModules
  const rects: React.ReactElement[] = []

  for (let row = 0; row < size; row++) {
    for (let col = 0; col < size; col++) {
      const isDark = data[row * size + col] === 1
      if (isDark) {
        rects.push(
          React.createElement('rect', {
            key: `${row}-${col}`,
            x: marginPx + col * cellPx,
            y: marginPx + row * cellPx,
            width: cellPx,
            height: cellPx,
            fill: '#000',
          })
        )
      }
    }
  }
  return rects
}

export function BarcodeRenderer({
  barcodeType,
  value,
  isPreview,
  width,
  height,
}: BarcodeRendererProps) {
  const svgRef = useRef<SVGSVGElement>(null)
  const isQR = barcodeType === 'qr'

  const displayValue =
    value || (barcodeType === 'ean13' ? '5901234123457' : '1234567890')

  // ===== 一维码：JsBarcode 渲染 =====
  useEffect(() => {
    if (isQR || !svgRef.current) return
    try {
      const format = FORMAT_MAP[barcodeType] || 'CODE128'
      JsBarcode(svgRef.current, displayValue, {
        format,
        width: 2,
        height: Math.max(40, height * 0.6),
        displayValue: true,
        fontSize: 12,
        margin: 4,
        background: '#ffffff',
      })
    } catch (_) {
      try {
        JsBarcode(svgRef.current, barcodeType === 'ean13' ? '5901234123457' : '123456789', {
          format: FORMAT_MAP[barcodeType] || 'CODE128',
          width: 2,
          height: 40,
          displayValue: true,
          fontSize: 12,
          margin: 4,
          background: '#ffffff',
        })
      } catch (_) { /* ignore */ }
    }
  }, [isQR, barcodeType, displayValue, height])

  // ===== 二维码：QRCode.create() 同步生成矩阵 → SVG rect =====
  const qrSvgContent = useMemo(() => {
    if (!isQR) return null
    try {
      const qr = QRCode.create(displayValue || 'QR', {
        errorCorrectionLevel: 'M',
      })
      const moduleSize = qr.modules.size
      // 计算单元格像素大小，让 QR 尺量尽量占满容器
      const availableSize = Math.min(width, height)
      const marginPx = availableSize * 0.04 // 约 4% 边距
      const cellPx = (availableSize - 2 * marginPx) / moduleSize

      const rects = buildQrSvgRects(qr.modules, cellPx, marginPx)
      const totalSvgSize = availableSize

      return { rects, totalSvgSize, moduleSize, cellPx, marginPx }
    } catch (err) {
      console.warn('[BarcodeRenderer] QR matrix creation failed:', err)
      return null
    }
  }, [isQR, displayValue, width, height])

  // ===== 渲染 =====
  return (
    <div
      className="barcode-renderer"
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#ffffff',
        overflow: 'hidden',
      }}
    >
      {isQR ? (
        qrSvgContent ? (
          <svg
            width={qrSvgContent.totalSvgSize}
            height={qrSvgContent.totalSvgSize}
            viewBox={`0 0 ${qrSvgContent.totalSvgSize} ${qrSvgContent.totalSvgSize}`}
            style={{ maxWidth: '100%', maxHeight: '100%' }}
          >
            {/* 白色底板 */}
            <rect
              x="0"
              y="0"
              width={qrSvgContent.totalSvgSize}
              height={qrSvgContent.totalSvgSize}
              fill="#fff"
            />
            {/* QR 矩阵 */}
            {qrSvgContent.rects}
          </svg>
        ) : (
          <span style={{ fontSize: 10, color: '#ccc' }}>二维码</span>
        )
      ) : (
        <svg ref={svgRef} />
      )}
    </div>
  )
}
