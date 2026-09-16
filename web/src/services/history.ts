// History service using localStorage — mirrors the Android HistoryManager

export interface HistoryItem {
  id: number;
  text: string;
  timestamp: number;
  languageCode: string;
  blockCount: number;
}

const STORAGE_KEY = 'helpmeread_history';
const MAX_ITEMS = 50;
let idCounter = 0;

function persist(items: HistoryItem[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  } catch {
    // Storage unavailable (private mode) or full — history stays in memory only
  }
}

export const HistoryService = {
  save(text: string, languageCode: string, blockCount: number): HistoryItem {
    const item: HistoryItem = {
      // Unique even for saves within the same millisecond
      id: Date.now() * 1000 + (idCounter++ % 1000),
      text,
      timestamp: Date.now(),
      languageCode,
      blockCount,
    };
    const items = this.getItems();
    items.unshift(item);
    persist(items.slice(0, MAX_ITEMS));
    return item;
  },

  getItems(): HistoryItem[] {
    try {
      const json = localStorage.getItem(STORAGE_KEY);
      return json ? (JSON.parse(json) as HistoryItem[]) : [];
    } catch {
      return [];
    }
  },

  deleteItem(id: number): void {
    persist(this.getItems().filter((i) => i.id !== id));
  },

  clearAll(): void {
    localStorage.removeItem(STORAGE_KEY);
  },

  formatTimestamp(ts: number): string {
    return new Date(ts).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  },
};
