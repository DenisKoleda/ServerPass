import { createRequire } from 'module'

const require = createRequire('D:/Documents/Minecraft/Mechanisms/package.json')
const mineflayer = require('mineflayer')

const host = process.env.MC_HOST ?? '127.0.0.1'
const port = Number(process.env.MC_PORT ?? 25566)
const mode = process.env.SP_E2E_MODE ?? 'correct'
const username = process.env.MC_BOT_NAME ?? `PassBot${Math.floor(Math.random() * 10000)}`
const password = process.env.SP_E2E_PASSWORD
const wrongPassword = process.env.SP_E2E_WRONG_PASSWORD
const version = process.env.MC_VERSION

if ((mode === 'correct' && !password) || (mode === 'wrong' && !wrongPassword)) {
  console.error('[serverpass-e2e] missing password env')
  process.exit(2)
}

const bot = mineflayer.createBot({
  host,
  port,
  username,
  auth: 'offline',
  ...(version ? { version } : {})
})

let kicked = false
let spawned = false

const timeout = setTimeout(() => {
  console.error(`[serverpass-e2e] timeout mode=${mode}`)
  try {
    bot.end('timeout')
  } finally {
    process.exit(3)
  }
}, 45000)

bot.once('spawn', async () => {
  spawned = true
  console.log(`[serverpass-e2e] spawned mode=${mode} user=${bot.username} version=${bot.version}`)
  if (mode === 'wrong') {
    for (let index = 0; index < 3; index++) {
      bot.chat(`/login ${wrongPassword}`)
      await wait(900)
    }
    await wait(4000)
    if (!kicked) {
      console.error('[serverpass-e2e] expected kick after wrong attempts')
      process.exitCode = 4
      bot.end('missing wrong-attempt kick')
    }
    return
  }

  bot.chat('/plugins')
  await wait(1200)
  bot.chat(`/login ${password}`)
  await wait(2500)
  if (!kicked) {
    console.log('[serverpass-e2e] correct login completed without kick')
    bot.end('correct login smoke complete')
  }
})

bot.on('message', message => {
  const text = message.toString()
  if (text.includes('/login')) {
    return
  }
  console.log(`[chat] ${text}`)
})

bot.on('kicked', reason => {
  kicked = true
  clearTimeout(timeout)
  console.log(`[serverpass-e2e] kicked mode=${mode}`)
  if (mode === 'wrong' && spawned) {
    process.exitCode = 0
  } else {
    process.exitCode = 5
  }
})

bot.on('error', error => {
  clearTimeout(timeout)
  console.error(`[serverpass-e2e] error: ${error.stack ?? error.message}`)
  process.exitCode = 6
})

bot.on('end', reason => {
  clearTimeout(timeout)
  console.log(`[serverpass-e2e] ended: ${reason ?? 'no reason'}`)
})

function wait(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}
