/**
 * 蓝牙标签打印机工具（支持热敏打印机）
 * 兼容 ESC/POS 指令集
 *
 * 支持环境：
 * - APP 端：使用 UniApp 蓝牙 API
 * - 浏览器（Chrome/Edge）：使用 Web Bluetooth API
 * - 不支持的环境：优雅降级提示
 */

// 检测可用的蓝牙 API
const hasUniBluetooth = typeof uni !== 'undefined' &&
  uni.openBluetoothAdapter !== undefined

// 更健壮的 Web Bluetooth 检测（兼容各种环境）
const hasWebBluetooth = (typeof window !== 'undefined' &&
  window.navigator &&
  'bluetooth' in window.navigator) ||
  (typeof navigator !== 'undefined' &&
  'bluetooth' in navigator)

const isBluetoothAvailable = hasUniBluetooth || hasWebBluetooth

// 获取蓝牙 API 的引用
const getBluetoothAPI = () => {
  if (typeof window !== 'undefined' && window.navigator && window.navigator.bluetooth) {
    return window.navigator.bluetooth
  }
  if (typeof navigator !== 'undefined' && navigator.bluetooth) {
    return navigator.bluetooth
  }
  return null
}

let currentEnvironment = null // 'uniapp' | 'web' | null
let currentDevice = null
let currentServer = null
let currentService = null
let writeCharacteristic = null

export const printerConfig = {
  serviceName: '00001101-0000-1000-8000-00805f9b34fb',  // 串口服务 SPP（小写！）
  deviceId: null,
  deviceName: '',
  connected: false,
}

let characteristic = null

export function initBluetooth() {
  console.log('🔍 初始化蓝牙...')
  console.log('   - hasUniBluetooth:', hasUniBluetooth)
  console.log('   - hasWebBluetooth:', hasWebBluetooth)
  console.log('   - typeof window:', typeof window)
  console.log('   - typeof navigator:', typeof navigator)
  console.log('   - window.navigator:', typeof window !== 'undefined' ? !!window.navigator : 'N/A')
  console.log('   - navigator.bluetooth:', typeof navigator !== 'undefined' ? 'bluetooth' in navigator : 'N/A')

  if (!isBluetoothAvailable) {
    // 提供更详细的错误信息
    const details = []
    if (typeof window === 'undefined') {
      details.push('window 对象不存在')
    } else if (!window.navigator) {
      details.push('window.navigator 不存在')
    } else if (!('bluetooth' in window.navigator)) {
      details.push('navigator.bluetooth 不存在（可能不是 Chrome/Edge 浏览器）')
      details.push('当前浏览器:', window.navigator.userAgent)
    }
    return Promise.reject(new Error('当前环境不支持蓝牙功能。' + details.join('; ')))
  }

  // 自动检测环境
  if (hasWebBluetooth && !hasUniBluetooth) {
    currentEnvironment = 'web'
    console.log('✅ 检测到 Web Bluetooth API（浏览器环境）')
    return Promise.resolve(true)
  }

  if (hasUniBluetooth) {
    currentEnvironment = 'uniapp'
    return new Promise((resolve, reject) => {
      uni.openBluetoothAdapter({
        success: () => {
          console.log('✅ UniApp 蓝牙适配器初始化成功')
          resolve(true)
        },
        fail: (err) => {
          console.error('❌ UniApp 蓝牙适配器初始化失败:', err)
          reject(new Error('请确保设备支持蓝牙并已开启'))
        },
      })
    })
  }

  return Promise.reject(new Error('未找到可用的蓝牙API'))
}

/**
 * 选择打印机（兼容两种环境）
 * - Web Bluetooth: 弹出系统选择框
 * - UniApp: 搜索并列出设备
 */
export function selectPrinter() {
  if (!isBluetoothAvailable) {
    return Promise.reject(new Error('当前环境不支持蓝牙'))
  }

  if (currentEnvironment === 'web') {
    return selectPrinterWeb()
  }

  if (currentEnvironment === 'uniapp') {
    return selectPrinterUniApp()
  }

  return Promise.reject(new Error('未初始化蓝牙环境'))
}

// Web Bluetooth: 使用系统选择对话框
function selectPrinterWeb() {
  const bluetooth = getBluetoothAPI()

  if (!bluetooth) {
    return Promise.reject(new Error('Web Bluetooth API 不可用'))
  }

  console.log('🔍 打开浏览器蓝牙设备选择...')
  console.log('📱 蓝牙 API 对象:', bluetooth)

  // 打印机通用服务UUID（串口服务）- 注意：必须是小写！
  const PRINTER_SERVICE_UUID = '00001101-0000-1000-8000-00805f9b34fb'  // 串口服务 SPP
  const GENERIC_ACCESS_SERVICE = '00001800-0000-1000-8000-00805f9b34fb'  // 通用访问

  // 尝试多种方式搜索打印机
  return bluetooth.requestDevice({
    filters: [
      { services: [PRINTER_SERVICE_UUID] },  // 串口服务（最常用）
    ],
    optionalServices: [
      GENERIC_ACCESS_SERVICE,
      '0000180a-0000-1000-8000-00805f9b34fb',  // 设备信息
      '0000ffe0-0000-1000-8000-00805f9b34fb',  // 自定义服务（很多打印机用）
    ],
  }).then(device => {
    console.log('✅ 用户选择了设备:', device.name)
    return {
      deviceId: device.id,
      name: device.name,
      device: device, // 保存原始设备对象
    }
  })
}

// UniApp: 搜索并返回列表
function selectPrinterUniApp(timeout = 10000) {
  return new Promise((resolve, reject) => {
    const devices = []

    uni.startBluetoothDevicesDiscovery({
      services: [printerConfig.serviceName],
      allowDuplicatesKey: false,
      success: () => {
        console.log('🔍 开始搜索蓝牙打印机...')

        const timer = setTimeout(() => {
          uni.stopBluetoothDevicesDiscovery()

          if (devices.length === 0) {
            reject(new Error('未找到蓝牙打印机，请确保打印机已开启并在附近'))
            return
          }

          // 返回设备列表，由UI层展示选择
          resolve(devices)
        }, timeout)

        uni.onBluetoothDeviceFound((res) => {
          res.devices.forEach(device => {
            if (device.name && !devices.find(d => d.deviceId === device.deviceId)) {
              devices.push({
                deviceId: device.deviceId,
                name: device.name,
                RSSI: device.RSSI || 0,
              })
              console.log('🖨️ 发现打印机:', device.name)
            }
          })
        })
      },
      fail: (err) => {
        console.error('❌ 搜索失败:', err)
        reject(new Error('蓝牙搜索失败: ' + (err.errMsg || '未知错误')))
      },
    })
  })
}

export async function connectPrinter(deviceIdOrDevice) {
  if (!isBluetoothAvailable) {
    throw new Error('当前环境不支持蓝牙连接')
  }

  try {
    uni.showLoading({ title: '连接打印机中...' })

    if (currentEnvironment === 'web') {
      // Web Bluetooth 连接
      await connectPrinterWeb(deviceIdOrDevice)
    } else if (currentEnvironment === 'uniapp') {
      // UniApp 蓝牙连接
      await connectPrinterUniApp(deviceIdOrDevice)
    } else {
      throw new Error('未初始化蓝牙环境')
    }

    printerConfig.connected = true

    uni.hideLoading()
    console.log('✅ 打印机连接成功')
    return true

  } catch (error) {
    uni.hideLoading()
    printerConfig.connected = false
    throw error
  }
}

// Web Bluetooth 连接
async function connectPrinterWeb(device) {
  currentDevice = device.device || device

  // 连接 GATT Server
  console.log('🔗 连接到 GATT Server...')
  currentServer = await currentDevice.gatt.connect()

  // 获取串口服务
  console.log('📡 获取打印服务...')
  currentService = await currentServer.getPrimaryService(printerConfig.serviceName)

  // 获取写入特征值
  writeCharacteristic = await currentService.getCharacteristic(
    '0000ffe1-0000-1000-8000-00805f9b34fb' || // 常见写入UUID
    currentService.characteristics[0]?.uuid   // 或使用第一个特征值
  )

  if (!writeCharacteristic) {
    // 尝试找到可写的特征值
    const characteristics = await currentService.getCharacteristics()
    writeCharacteristic = characteristics.find(c =>
      c.properties.write || c.properties.writeWithoutResponse
    )
  }

  if (!writeCharacteristic) {
    throw new Error('未找到打印机的写入特征值')
  }

  printerConfig.deviceId = currentDevice.id
  printerConfig.deviceName = currentDevice.name

  // 监听断开事件
  currentServer.addEventListener('gattserverdisconnected', () => {
    console.log('⚠️ 蓝牙连接已断开')
    printerConfig.connected = false
    currentDevice = null
    currentServer = null
    currentService = null
    writeCharacteristic = null
  })
}

// UniApp 蓝牙连接
async function connectPrinterUniApp(deviceId) {
  await createBLEConnection(deviceId)
  await discoverServices()
  printerConfig.deviceId = deviceId
}

function createBLEConnection(deviceId) {
  return new Promise((resolve, reject) => {
    uni.createBLEConnection({
      deviceId,
      timeout: 10000,
      success: () => {
        currentDevice = deviceId
        setTimeout(resolve, 1000)
      },
      fail: (err) => {
        reject(new Error('连接打印机失败: ' + (err.errMsg || '未知错误')))
      },
    })
  })
}

function discoverServices() {
  return new Promise((resolve, reject) => {
    uni.getBLEDeviceServices({
      deviceId: currentDevice,
      success: (res) => {
        const service = res.services.find(s => 
          s.uuid.includes('FFE0') || s.uuid.includes('FF10')
        )
        
        if (!service) {
          reject(new Error('未找到打印服务'))
          return
        }
        
        getCharacteristics(service.uuid).then(resolve).catch(reject)
      },
      fail: reject,
    })
  })
}

function getCharacteristics(serviceId) {
  return new Promise((resolve, reject) => {
    uni.getBLEDeviceCharacteristics({
      deviceId: currentDevice,
      serviceId,
      success: (res) => {
        const char = res.characteristics.find(c => 
          c.properties.write || c.properties.writeNoResponse
        )
        
        if (!char) {
          reject(new Error('未找到写入特征值'))
          return
        }
        
        characteristic = {
          serviceId,
          characteristicId: char.uuid,
          writeType: char.properties.writeNoResponse ? 'writeNoResponse' : 'write',
        }
        resolve()
      },
      fail: reject,
    })
  })
}

export async function disconnectPrinter() {
  try {
    if (currentEnvironment === 'web' && currentServer) {
      // Web Bluetooth 断开
      if (currentServer.connected) {
        await currentServer.disconnect()
      }
      console.log('✅ Web Bluetooth 已断开')
    } else if (currentEnvironment === 'uniapp' && printerConfig.deviceId) {
      // UniApp 蓝牙断开
      await new Promise((resolve, reject) => {
        uni.closeBLEConnection({
          deviceId: printerConfig.deviceId,
          success: () => resolve(),
          fail: reject,
        })
      })
      console.log('✅ UniApp 蓝牙已断开')
    }

    printerConfig.deviceId = null
    printerConfig.deviceName = ''
    printerConfig.connected = false

    currentDevice = null
    currentServer = null
    currentService = null
    writeCharacteristic = null
    characteristic = null

  } catch (error) {
    console.error('❌ 断开连接时出错:', error)
  }
}

export async function sendToPrinter(labelData) {
  if (!printerConfig.connected) {
    throw new Error('打印机未连接，请先连接蓝牙打印机')
  }

  // 检查对应环境的连接状态
  if (currentEnvironment === 'web' && !writeCharacteristic) {
    throw new Error('Web Bluetooth 未就绪')
  }
  if (currentEnvironment === 'uniapp' && !characteristic) {
    throw new Error('UniApp 蓝牙未就绪')
  }

  try {
    const commands = generateLabelCommands(labelData)
    await sendPrintData(commands)
    console.log('🏷️ 标签打印完成:', labelData.reportNo)
  } catch (error) {
    console.error('❌ 打印失败:', error)
    throw error
  }
}

function generateLabelCommands(data) {
  const buffer = []
  
  buffer.push(...ESC_POS.INIT)
  buffer.push(...ESC_POS.ALIGN_CENTER)
  
  buffer.push(...ESC_POS.BOLD_ON)
  buffer.push(...textToBytes('MES 称重标签'))
  buffer.push(...ESC_POS.BOLD_OFF)
  buffer.push(...ESC_POS.LINE_FEED)
  
  buffer.push(...ESC_POS.LINE_FEED)
  buffer.push(...textToBytes(`工单号: ${data.moNo || '-'}`))
  buffer.push(...textToBytes(`报工单: ${data.reportNo || '-'}`))
  buffer.push(...ESC_POS.LINE_FEED)
  
  buffer.push(...textToBytes(`产品: ${data.productName || '-'}`))
  if (data.productCode) {
    buffer.push(...textToBytes(`编码: ${data.productCode}`))
  }
  buffer.push(...ESC_POS.LINE_FEED)
  
  buffer.push(...ESC_POS.DOUBLE_HEIGHT_ON)
  buffer.push(...ESC_POS.DOUBLE_WIDTH_ON)
  buffer.push(...textToBytes(`重量: ${data.weightKg || '0.00'} kg`))
  buffer.push(...ESC_POS.DOUBLE_HEIGHT_OFF)
  buffer.push(...ESC_POS.DOUBLE_WIDTH_OFF)
  buffer.push(...ESC_POS.LINE_FEED)
  
  if (data.qty != null) {
    buffer.push(...textToBytes(`数量: ${data.qty}`))
  }
  
  buffer.push(...textToBytes(`时间: ${new Date().toLocaleString()}`))
  buffer.push(...ESC_POS.LINE_FEED)
  buffer.push(...ESC_POS.LINE_FEED)
  
  buffer.push(...ESC_POS.CUT_PAPER)
  
  return new Uint8Array(buffer)
}

function textToBytes(text) {
  const bytes = []
  for (let i = 0; i < text.length; i++) {
    bytes.push(text.charCodeAt(i) & 0xFF)
  }
  return bytes
}

async function sendPrintData(data) {
  if (currentEnvironment === 'web') {
    // Web Bluetooth 发送数据
    await sendPrintDataWeb(data)
  } else if (currentEnvironment === 'uniapp') {
    // UniApp 蓝牙发送数据
    await sendPrintDataUniApp(data)
  } else {
    throw new Error('未知的蓝牙环境')
  }
}

// Web Bluetooth 发送数据
async function sendPrintDataWeb(data) {
  console.log('📤 通过 Web Bluetooth 发送打印数据...', data.length, '字节')

  try {
    // 尝试使用 writeWithoutResponse（更快）
    if (writeCharacteristic.properties.writeWithoutResponse) {
      await writeCharacteristic.writeValueWithoutResponse(data)
    } else {
      // 否则使用普通写入（需要响应）
      await writeCharacteristic.writeValue(data)
    }

    console.log('✅ Web Bluetooth 数据发送成功')
  } catch (error) {
    console.error('❌ Web Bluetooth 发送失败:', error)
    throw new Error('蓝牙发送失败: ' + error.message)
  }
}

// UniApp 蓝牙发送数据
function sendPrintDataUniApp(data) {
  return new Promise((resolve, reject) => {
    const chunkSize = 20
    let offset = 0

    function sendChunk() {
      if (offset >= data.length) {
        resolve()
        return
      }

      const chunk = data.slice(offset, offset + chunkSize)

      uni.writeBLECharacteristicValue({
        deviceId: currentDevice,
        serviceId: characteristic.serviceId,
        characteristicId: characteristic.characteristicId,
        value: chunk.buffer,
        writeType: characteristic.writeType === 'writeNoResponse' 
          ? 'writeNoResponse' 
          : 'write',
        success: () => {
          offset += chunkSize
          setTimeout(sendChunk, 50)
        },
        fail: (err) => {
          reject(new Error('数据发送失败: ' + (err.errMsg || '未知错误')))
        },
      })
    }
    
    sendChunk()
  })
}

const ESC_POS = {
  INIT: [0x1B, 0x40],
  LINE_FEED: [0x0A],
  CUT_PAPER: [0x1D, 0x56, 0x00],
  BOLD_ON: [0x1B, 0x45, 0x01],
  BOLD_OFF: [0x1B, 0x45, 0x00],
  ALIGN_LEFT: [0x1B, 0x61, 0x00],
  ALIGN_CENTER: [0x1B, 0x61, 0x01],
  ALIGN_RIGHT: [0x1B, 0x61, 0x02],
  DOUBLE_HEIGHT_ON: [0x1B, 0x21, 0x10],
  DOUBLE_HEIGHT_OFF: [0x1B, 0x21, 0x00],
  DOUBLE_WIDTH_ON: [0x1B, 0x21, 0x20],
  DOUBLE_WIDTH_OFF: [0x1B, 0x21, 0x00],
}

export function isPrinterConnected() {
  return printerConfig.connected
}

export function getConnectedDeviceInfo() {
  return {
    deviceId: printerConfig.deviceId,
    connected: printerConfig.connected,
  }
}