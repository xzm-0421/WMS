/**
 * PDA 硬件扫码兜底：部分机型扫码不走 WebView input / window.keydown，
 * 只走 plus.key 或厂商广播。页面「能扫到码但没反应」时靠这里收码。
 */

const BROADCAST_ACTIONS = [
  'android.intent.action.SCANRESULT',
  'android.intent.action.SCAN_RESULT',
  'android.intent.ACTION_DECODE_DATA',
  'android.intent.action.RECEIVE_SCANDATA_BROADCAST',
  'com.android.server.scannerservice.broadcast',
  'scan.rcv.message',
  'nlscan.action.SCANNER_RESULT',
  'com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED',
  'com.scanner.broadcast',
  'com.symbol.datawedge.api.RESULT_ACTION',
  'com.urovo.pos.app.scan',
]

const EXTRA_KEYS = [
  'scannerdata',
  'barcode',
  'barcode_string',
  'barcodeData',
  'data',
  'code',
  'value',
  'ScanResult',
  'scan_data',
  'scanner_data',
  'SCAN_BARCODE1',
  'com.symbol.datawedge.data_string',
  'SCAN_DECODE_DATA',
]

export function keyCodeToChar(keyCode) {
  const code = Number(keyCode)
  if (!Number.isFinite(code)) return null
  if (code === 13) return 'Enter'
  if (code === 8) return 'Backspace'
  if (code === 9 || code === 27 || code === 16 || code === 17 || code === 18) return null
  if (code === 32) return ' '
  if (code >= 48 && code <= 57) return String.fromCharCode(code)
  if (code >= 65 && code <= 90) return String.fromCharCode(code)
  if (code >= 96 && code <= 105) return String(code - 96)
  if (code === 106) return '*'
  if (code === 107 || code === 187) return '='
  if (code === 109 || code === 189) return '-'
  if (code === 110 || code === 190) return '.'
  if (code === 111 || code === 191) return '/'
  if (code === 186) return ';'
  if (code === 188) return ','
  if (code === 192) return '`'
  if (code === 219) return '{'
  if (code === 220) return '\\'
  if (code === 221) return '}'
  if (code === 222) return '"'
  return null
}

export function bindPlusKey(onKey) {
  try {
    // eslint-disable-next-line no-undef
    const plusObj = typeof plus !== 'undefined' ? plus : null
    if (!plusObj?.key?.addEventListener) return () => {}
    const handler = (e) => onKey(e)
    plusObj.key.addEventListener('keydown', handler)
    return () => {
      try {
        plusObj.key.removeEventListener('keydown', handler)
      } catch {
        // ignore
      }
    }
  } catch {
    return () => {}
  }
}

export function bindAndroidScanBroadcast(onCode) {
  try {
    // eslint-disable-next-line no-undef
    const plusObj = typeof plus !== 'undefined' ? plus : null
    if (!plusObj?.android?.runtimeMainActivity) return () => {}
    const main = plusObj.android.runtimeMainActivity()
    const IntentFilter = plusObj.android.importClass('android.content.IntentFilter')
    const filter = new IntentFilter()
    BROADCAST_ACTIONS.forEach((action) => filter.addAction(action))
    const receiver = plusObj.android.implements(
      'io.dcloud.feature.internal.reflect.BroadcastReceiver',
      {
        onReceive(_context, intent) {
          try {
            plusObj.android.importClass(intent)
            let code = ''
            for (const key of EXTRA_KEYS) {
              const value = intent.getStringExtra(key)
              if (value) {
                code = String(value)
                break
              }
            }
            code = (code || '').replace(/[\r\n\t]/g, '').trim()
            if (code) onCode(code)
          } catch {
            // ignore vendor extras we cannot read
          }
        },
      },
    )
    main.registerReceiver(receiver, filter)
    return () => {
      try {
        main.unregisterReceiver(receiver)
      } catch {
        // ignore
      }
    }
  } catch {
    return () => {}
  }
}
