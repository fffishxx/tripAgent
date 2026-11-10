import type {
  TripDetail,
  TripDraft,
  TripDraftDay,
  TripDraftItem,
  TripPlan,
  TripPlanPayload,
  TripUpdatePayload,
  TripCreatePayload,
} from '@/types/trip'
import { createId } from '@/utils/format'
import {
  ITEM_TYPE_OPTIONS,
  TIME_SLOT_OPTIONS,
  enumerateDateRange,
} from '@/utils/trip'

const ITEM_TYPES = new Set<string>(ITEM_TYPE_OPTIONS.map((option) => option.value))
const TIME_SLOTS = new Set<string>(TIME_SLOT_OPTIONS)

export function createDraftItem(
  overrides: Partial<TripDraftItem> = {},
): TripDraftItem {
  return {
    localId: createId('item-'),
    sourceId: null,
    itemType: 'ATTRACTION',
    name: '',
    address: '',
    longitude: null,
    latitude: null,
    timeSlot: '上午',
    durationMin: 60,
    reason: '',
    notes: '',
    externalPoiId: null,
    ...overrides,
  }
}

export function createEmptyTripDraft(): TripDraft {
  return {
    title: '',
    destination: '',
    startDate: '',
    endDate: '',
    description: '',
    days: [],
  }
}

export function createTripDraftFromDetail(detail: TripDetail): TripDraft {
  return {
    title: detail.title ?? '',
    destination: detail.plan?.destination || detail.destination || '',
    startDate: detail.startDate,
    endDate: detail.endDate,
    description: detail.description ?? '',
    days: (detail.plan?.days ?? []).map((day) => ({
      dayIndex: day.dayIndex,
      dayDate: day.dayDate,
      theme: day.theme ?? '',
      items: (day.items ?? []).map((item) =>
        createDraftItem({
          sourceId: item.id,
          itemType: item.itemType || 'ATTRACTION',
          name: item.name ?? '',
          address: item.address ?? '',
          longitude: item.longitude,
          latitude: item.latitude,
          timeSlot: item.timeSlot ?? '',
          durationMin: item.durationMin,
          reason: item.reason ?? '',
          notes: item.notes ?? '',
          externalPoiId: item.externalPoiId ?? null,
        }),
      ),
    })),
  }
}

export interface RebuildDaysResult {
  days: TripDraftDay[]
  removedItemCount: number
}

/**
 * 根据日期区间重建每日安排。后续日期保留同 dayIndex 的旧安排，超出新区间的安排会被移除。
 */
export function buildDraftDaysForRange(
  startDate: string,
  endDate: string,
  existingDays: TripDraftDay[] = [],
): RebuildDaysResult {
  const dates = enumerateDateRange(startDate, endDate)
  const oldDays = new Map(existingDays.map((day) => [day.dayIndex, day]))
  const oldItemIds = new Set(
    existingDays.flatMap((day) => day.items.map((item) => item.localId)),
  )

  const days = dates.map((date, index) => {
    const oldDay = oldDays.get(index + 1)
    return {
      dayIndex: index + 1,
      dayDate: date,
      theme: oldDay?.theme ?? '',
      items: oldDay?.items ?? [],
    }
  })

  const retainedIds = new Set(
    days.flatMap((day) => day.items.map((item) => item.localId)),
  )
  let removedItemCount = 0
  for (const id of oldItemIds) {
    if (!retainedIds.has(id)) {
      removedItemCount += 1
    }
  }

  return { days, removedItemCount }
}

export function countDraftItems(days: TripDraftDay[]): number {
  return days.reduce((total, day) => total + day.items.length, 0)
}

export function snapshotTripDraft(draft: TripDraft): string {
  return JSON.stringify({
    title: draft.title,
    destination: draft.destination,
    startDate: draft.startDate,
    endDate: draft.endDate,
    description: draft.description,
    days: draft.days.map((day) => ({
      dayIndex: day.dayIndex,
      theme: day.theme,
      items: day.items.map((item) => ({
        itemType: item.itemType,
        name: item.name,
        address: item.address,
        longitude: item.longitude,
        latitude: item.latitude,
        timeSlot: item.timeSlot,
        durationMin: item.durationMin,
        reason: item.reason,
        notes: item.notes,
        externalPoiId: item.externalPoiId,
      })),
    })),
  })
}

export function validateTripDraft(
  draft: TripDraft,
  options: { requireDestination?: boolean; requireDates?: boolean } = {},
): string | null {
  const { requireDestination = true, requireDates = true } = options

  if (requireDestination && !draft.destination.trim()) {
    return '请填写目的地'
  }
  if (draft.destination.trim().length > 64) {
    return '目的地不能超过 64 个字符'
  }
  if (draft.title.trim().length > 100) {
    return '行程名称不能超过 100 个字符'
  }
  if (draft.description.trim().length > 1000) {
    return '行程描述不能超过 1000 个字符'
  }

  if (requireDates) {
    if (!draft.startDate || !draft.endDate) {
      return '请选择行程日期'
    }
    const expectedDates = enumerateDateRange(draft.startDate, draft.endDate)
    if (expectedDates.length === 0) {
      return '开始日期不能晚于结束日期'
    }
    if (expectedDates.length !== draft.days.length) {
      return '日期范围与每日安排不一致'
    }
  }

  if (draft.days.length === 0) {
    return '请先选择行程日期'
  }

  if (countDraftItems(draft.days) === 0) {
    return '请至少添加一个景点或安排'
  }

  for (const day of draft.days) {
    if (day.items.length > 20) {
      return `第${day.dayIndex}天的安排不能超过 20 项`
    }
    if (day.theme.trim().length > 100) {
      return `第${day.dayIndex}天的主题不能超过 100 个字符`
    }

    for (const item of day.items) {
      const name = item.name.trim()
      if (!name) {
        return `第${day.dayIndex}天有安排未填写名称`
      }
      if (name.length > 128) {
        return `第${day.dayIndex}天的景点名称不能超过 128 个字符`
      }
      if (item.address.trim().length > 255) {
        return `第${day.dayIndex}天的地址不能超过 255 个字符`
      }
      if (item.reason.trim().length > 500) {
        return `第${day.dayIndex}天的安排理由不能超过 500 个字符`
      }
      if (item.notes.trim().length > 500) {
        return `第${day.dayIndex}天的备注不能超过 500 个字符`
      }
      if (item.itemType && !ITEM_TYPES.has(item.itemType)) {
        return `第${day.dayIndex}天的安排类型不合法`
      }
      if (item.timeSlot && !TIME_SLOTS.has(item.timeSlot)) {
        return `第${day.dayIndex}天的时间段不合法`
      }
      if (
        item.durationMin !== null &&
        (item.durationMin < 1 || item.durationMin > 1440)
      ) {
        return `第${day.dayIndex}天的停留时长需在 1-1440 分钟之间`
      }

      const hasLongitude = item.longitude !== null
      const hasLatitude = item.latitude !== null
      if (hasLongitude !== hasLatitude) {
        return `「${name}」的经纬度必须同时填写`
      }
      if (hasLongitude && hasLatitude) {
        if (
          item.longitude! < -180 ||
          item.longitude! > 180 ||
          item.latitude! < -90 ||
          item.latitude! > 90
        ) {
          return `「${name}」的经纬度超出范围`
        }
      }
    }
  }

  return null
}

function cleanText(value: string | null | undefined): string | null {
  const trimmed = value?.trim() ?? ''
  return trimmed || null
}

export function buildTripPlanPayload(
  draft: TripDraft,
  extras?: TripPlan | null,
): TripPlanPayload {
  const plan: TripPlanPayload = {
    destination: draft.destination.trim(),
    days: draft.days.map((day) => ({
      dayIndex: day.dayIndex,
      theme: cleanText(day.theme),
      items: day.items.map((item) => ({
        itemType: item.itemType || 'ATTRACTION',
        name: item.name.trim(),
        address: cleanText(item.address),
        longitude: item.longitude,
        latitude: item.latitude,
        timeSlot: cleanText(item.timeSlot),
        durationMin: item.durationMin,
        reason: cleanText(item.reason),
        externalPoiId: item.externalPoiId,
        notes: cleanText(item.notes),
      })),
    })),
  }

  if (extras?.lodgingArea) {
    plan.lodgingArea = {
      name: extras.lodgingArea.name ?? null,
      reason: extras.lodgingArea.reason ?? null,
    }
  }
  if (extras?.diningSuggestions?.length) {
    plan.diningSuggestions = extras.diningSuggestions.map((suggestion) => ({
      area: suggestion.area ?? null,
      cuisine: suggestion.cuisine ?? null,
      note: suggestion.note ?? null,
    }))
  }
  if (extras?.budgetEstimate) {
    plan.budgetEstimate = {
      total: extras.budgetEstimate.total ?? null,
      breakdown: extras.budgetEstimate.breakdown ?? null,
    }
  }
  if (extras?.assumptions) {
    plan.assumptions = {
      days: extras.assumptions.days ?? null,
      crowd: extras.assumptions.crowd ?? null,
      budgetLevel: extras.assumptions.budgetLevel ?? null,
      notes: extras.assumptions.notes ? [...extras.assumptions.notes] : null,
    }
  }

  return plan
}

export function buildCreateTripPayload(draft: TripDraft): TripCreatePayload {
  return {
    title: cleanText(draft.title) ?? undefined,
    startDate: draft.startDate,
    endDate: draft.endDate,
    description: cleanText(draft.description) ?? undefined,
    plan: buildTripPlanPayload(draft),
  }
}

export function buildUpdateTripPayload(
  detail: TripDetail,
  draft: TripDraft,
): TripUpdatePayload {
  return {
    version: detail.version,
    title: cleanText(draft.title) ?? cleanText(detail.title) ?? undefined,
    startDate: draft.startDate,
    endDate: draft.endDate,
    description: cleanText(draft.description) ?? undefined,
    plan: buildTripPlanPayload(draft, detail.plan),
  }
}
