<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { api, type Seat, type NotificationItem, type WaitPosition } from './api'

const rooms = ref<{ id: number; name: string; floor: number }[]>([])
const roomId = ref(1)
const seats = ref<Seat[]>([])
const userId = ref(1001)
const msg = ref('')
const ticket = ref<{ id: number; status: string; checkinCode: string } | null>(null)
const loading = ref(false)
const notifications = ref<NotificationItem[]>([])
const unreadCount = ref(0)
const waitPositions = ref<WaitPosition[]>([])

function iso(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`
}

const now = new Date()
const start = ref(iso(new Date(now.getFullYear(), now.getMonth(), now.getDate(), 8, 0)))
const end = ref(iso(new Date(now.getFullYear(), now.getMonth(), now.getDate(), 12, 0)))

const stats = computed(() => ({
  total: seats.value.length,
  free: seats.value.filter(s => !s.busy).length
}))

async function load() {
  loading.value = true
  msg.value = ''
  try {
    rooms.value = await api.rooms()
    seats.value = await api.seats(roomId.value, start.value, end.value)
  } catch (e: any) {
    msg.value = e.message
  } finally {
    loading.value = false
  }
}

async function pick(seat: Seat) {
  if (seat.status === 'DISABLED') {
    msg.value = `座位 ${seat.code} 已停用，无法预约或候补`
    return
  }
  if (seat.busy) {
    await joinWait(seat)
    return
  }
  try {
    const r = await api.reserve(userId.value, seat.id, start.value, end.value)
    ticket.value = r
    msg.value = `预约成功：${seat.code}，凭签到码到馆扫码`
    await load()
  } catch (e: any) {
    msg.value = e.message
  }
}

async function joinWait(seat: Seat) {
  try {
    const p = await api.joinWaitlist(userId.value, seat.id, start.value, end.value)
    msg.value = `已登记候补 ${seat.code}：前面还有 ${p.aheadCount} 人，预计等待 ${p.estimatedWaitMinutes} 分钟`
    await loadWaitPositions()
  } catch (e: any) {
    msg.value = e.message
  }
}

async function checkin() {
  if (!ticket.value) return
  try {
    await api.checkin(ticket.value.checkinCode)
    msg.value = '签到成功，座位进入使用中'
    ticket.value = { ...ticket.value, status: 'USING' }
    await load()
  } catch (e: any) {
    msg.value = e.message
  }
}

async function away() {
  if (!ticket.value) return
  try {
    await api.away(ticket.value.id, 30)
    ticket.value = { ...ticket.value, status: 'AWAY' }
    msg.value = '已暂离，30 分钟内返回有效'
    await load()
  } catch (e: any) {
    msg.value = e.message
  }
}

async function back() {
  if (!ticket.value) return
  try {
    await api.back(ticket.value.id)
    ticket.value = { ...ticket.value, status: 'USING' }
    msg.value = '已返回，座位恢复使用中'
    await load()
  } catch (e: any) {
    msg.value = e.message
  }
}

async function cancel() {
  if (!ticket.value) return
  try {
    const r = await api.cancel(ticket.value.id, userId.value)
    ticket.value = { ...ticket.value, status: r.status }
    msg.value = '预约已取消，座位已释放'
    await load()
  } catch (e: any) {
    msg.value = e.message
  }
}

async function finish() {
  if (!ticket.value) return
  try {
    const r = await api.finish(ticket.value.id)
    ticket.value = { ...ticket.value, status: r.status }
    msg.value = '已结束使用，座位正常释放'
    await load()
  } catch (e: any) {
    msg.value = e.message
  }
}

async function loadNotifications() {
  try {
    notifications.value = await api.notifications(userId.value)
    await loadUnreadCount()
  } catch (e: any) {
    msg.value = e.message
  }
}

async function loadUnreadCount() {
  try {
    unreadCount.value = await api.unreadCount(userId.value)
  } catch (e: any) {
    msg.value = e.message
  }
}

async function markRead(id: number) {
  try {
    await api.markRead(id)
    const n = notifications.value.find(x => x.id === id)
    if (n) n.read = true
    await loadUnreadCount()
  } catch (e: any) {
    msg.value = e.message
  }
}

async function markAllRead() {
  try {
    await api.markAllRead(userId.value)
    notifications.value.forEach(n => { n.read = true })
    unreadCount.value = 0
  } catch (e: any) {
    msg.value = e.message
  }
}

async function loadWaitPositions() {
  try {
    waitPositions.value = await api.waitPosition(userId.value)
  } catch (e: any) {
    msg.value = e.message
  }
}

function seatClass(seat: Seat) {
  if (seat.status === 'DISABLED') return 'seat disabled'
  if (seat.status === 'USING') return 'seat using'
  if (seat.status === 'AWAY') return 'seat away'
  if (seat.status === 'RESERVED' || seat.busy) return 'seat busy'
  return 'seat free'
}

onMounted(() => {
  load()
  loadUnreadCount()
  loadWaitPositions()
})
</script>

<template>
  <main class="page">
    <header>
      <h1>智慧自习室座位预约与占座检测</h1>
      <p class="sub">选座 → 预约 → 扫码签到 → 暂离 / 超时释放，全流程演示</p>
    </header>

    <section class="bar">
      <label>自习室
        <select v-model.number="roomId" @change="load">
          <option v-for="r in rooms" :key="r.id" :value="r.id">{{ r.name }}（{{ r.floor }} 层）</option>
        </select>
      </label>
      <label>开始 <input v-model="start" type="datetime-local" @change="load" /></label>
      <label>结束 <input v-model="end" type="datetime-local" @change="load" /></label>
      <label>学号 <input v-model.number="userId" type="number" /></label>
      <button @click="load" :disabled="loading">{{ loading ? '加载中…' : '刷新座位' }}</button>
      <button class="notify-btn" @click="loadNotifications">
        查看通知
        <span v-if="unreadCount" class="badge">{{ unreadCount }}</span>
      </button>
      <span class="stat">空闲 {{ stats.free }} / {{ stats.total }}</span>
    </section>

    <p v-if="msg" class="msg">{{ msg }}</p>

    <section class="legend">
      <span><i class="dot free"></i>空闲</span>
      <span><i class="dot busy"></i>该时段已预约</span>
      <span><i class="dot using"></i>使用中</span>
      <span><i class="dot away"></i>暂离</span>
      <span><i class="dot disabled"></i>停用</span>
    </section>

    <section class="grid">
      <button
        v-for="seat in seats"
        :key="seat.id"
        :class="seatClass(seat)"
        @click="pick(seat)"
      >
        <strong>{{ seat.code }}</strong>
        <small>{{ seat.feature }}</small>
      </button>
    </section>

    <section v-if="ticket" class="ticket">
      <h2>预约单 #{{ ticket.id }}（{{ ticket.status }}）</h2>
      <p class="code">{{ ticket.checkinCode }}</p>
      <div class="actions">
        <button @click="checkin">模拟扫码签到</button>
        <button @click="away">暂离 30 分钟</button>
        <button @click="back">返回座位</button>
        <button @click="finish">结束使用</button>
        <button @click="cancel" class="ghost">取消预约</button>
      </div>
      <p class="hint">演示环境默认把签到码直接显示出来；真机上该码渲染为二维码，由自助机或手机扫描。</p>
    </section>

    <section v-if="notifications.length" class="notifications">
      <div class="section-head">
        <h2>我的通知（{{ notifications.length }}）<span v-if="unreadCount" class="badge">{{ unreadCount }}</span></h2>
        <button v-if="unreadCount" class="link-btn" @click="markAllRead">全部已读</button>
      </div>
      <ul>
        <li v-for="n in notifications" :key="n.id" :class="{ unread: !n.read }">
          <div class="n-title">
            <strong>{{ n.title }}</strong>
            <button v-if="!n.read" class="link-btn" @click="markRead(n.id)">标为已读</button>
          </div>
          <span>{{ n.content }}</span>
          <small>{{ n.createdAt }}</small>
        </li>
      </ul>
    </section>

    <section v-if="waitPositions.length" class="waitlist">
      <div class="section-head">
        <h2>我的候补（{{ waitPositions.length }}）</h2>
        <button class="link-btn" @click="loadWaitPositions">刷新排队位置</button>
      </div>
      <ul>
        <li v-for="p in waitPositions" :key="p.id" class="wait-row">
          <span>座位 #{{ p.seatId }}</span>
          <span>前面还有 {{ p.aheadCount }} 人</span>
          <span>预计等待 {{ p.estimatedWaitMinutes }} 分钟</span>
        </li>
      </ul>
    </section>
  </main>
</template>
