import React from 'react';
import { useTheme } from '../context/ThemeContext';
import type { HealthResponse } from '../services/api';
import { NAV_ITEMS, type ScreenId } from '../types/navigation';

interface DesktopSidebarProps {
  activeScreen: ScreenId;
  onSelectScreen: (screen: ScreenId) => void;
  health: HealthResponse | null;
  healthLoading: boolean;
  onRefreshHealth: () => void;
}

export const DesktopSidebar: React.FC<DesktopSidebarProps> = ({
  activeScreen,
  onSelectScreen,
  health,
  healthLoading,
  onRefreshHealth,
}) => {
  const { theme, toggleTheme } = useTheme();
  const isGraphOnline = health?.falkordb === 'UP';

  return (
    <aside className="hidden md:flex flex-col w-64 h-screen fixed left-0 top-0 bg-surface-container-low border-r border-outline-variant/20 z-40 select-none">
      {/* Branding */}
      <div className="p-space-md border-b border-outline-variant/20 flex flex-col gap-space-xs">
        <div className="flex items-center gap-space-sm">
          <div className="w-8 h-8 rounded-lg bg-primary-container/20 border border-primary-container/40 flex items-center justify-center flex-shrink-0">
            <span className="material-symbols-outlined text-primary-container text-[20px]">
              grain
            </span>
          </div>
          <div className="flex flex-col">
            <span className="font-headline-sm text-headline-sm font-semibold tracking-tight text-primary">
              Chittix
            </span>
            <span className="font-code-sm text-[10px] text-secondary tracking-wider uppercase">
              Company Intelligence
            </span>
          </div>
        </div>

        <div className="mt-2 px-2 py-1 rounded bg-surface-container flex items-center justify-between text-[11px] font-code-sm">
          <span className="text-on-surface-variant">GRAPH:</span>
          <span className="text-primary font-mono truncate max-w-[120px]">
            {health?.graph || 'company_brain'}
          </span>
        </div>
      </div>

      {/* Nav List */}
      <nav className="flex-1 px-space-sm py-space-md flex flex-col gap-1.5 overflow-y-auto">
        <div className="px-space-sm pb-1 text-[10px] font-code-sm uppercase tracking-wider text-outline">
          Navigation
        </div>
        {NAV_ITEMS.map(item => {
          const isActive = activeScreen === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onSelectScreen(item.id)}
              className={`w-full flex items-center gap-space-sm px-space-md py-2.5 rounded-xl font-label-md transition-all text-left ${
                isActive
                  ? 'bg-surface-container-high text-primary font-semibold shadow-sm border-l-2 border-primary-container'
                  : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
              }`}
            >
              <span
                className={`material-symbols-outlined text-[20px] ${
                  isActive ? 'text-primary-container' : 'text-on-surface-variant'
                }`}
              >
                {item.icon}
              </span>
              <span className="truncate">{item.label}</span>
            </button>
          );
        })}
      </nav>

      {/* Footer Controls: Theme Toggle & Backend Health Status */}
      <div className="p-space-md border-t border-outline-variant/20 flex flex-col gap-space-sm bg-surface-container-lowest">
        {/* Theme Toggle Button */}
        <button
          onClick={toggleTheme}
          className="w-full flex items-center justify-between px-space-sm py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-label-sm font-label-sm transition-colors"
        >
          <span className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[16px] text-secondary">
              {theme === 'dark' ? 'dark_mode' : 'light_mode'}
            </span>
            <span className="capitalize">{theme} Theme</span>
          </span>
          <span className="font-code-sm text-[10px] text-outline uppercase">
            Toggle
          </span>
        </button>

        {/* Backend Health telemetry badge */}
        <div className="flex items-center justify-between px-1 text-[11px] font-code-sm">
          <div className="flex items-center gap-1.5">
            <span
              className={`w-2 h-2 rounded-full ${
                isGraphOnline ? 'bg-tertiary-container animate-pulse' : 'bg-error'
              }`}
            />
            <span className="text-on-surface-variant">
              {healthLoading
                ? 'Connecting...'
                : isGraphOnline
                ? 'FalkorDB Online'
                : 'FalkorDB Standby'}
            </span>
          </div>
          <button
            onClick={onRefreshHealth}
            title="Refresh connection status"
            className="text-secondary hover:underline cursor-pointer"
          >
            check
          </button>
        </div>
      </div>
    </aside>
  );
};

interface MobileBottomNavProps {
  activeScreen: ScreenId;
  onSelectScreen: (screen: ScreenId) => void;
}

export const MobileBottomNav: React.FC<MobileBottomNavProps> = ({
  activeScreen,
  onSelectScreen,
}) => {
  return (
    <nav className="md:hidden fixed bottom-0 w-full z-50 pb-safe bg-surface-dim/90 backdrop-blur-xl border-t border-outline-variant/20 shadow-[0_-4px_20px_rgba(0,0,0,0.45)]">
      <div className="h-16 px-space-xs flex items-center justify-around max-w-md mx-auto">
        {NAV_ITEMS.map(item => {
          const isActive = activeScreen === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onSelectScreen(item.id)}
              className={`flex flex-col items-center justify-center gap-0.5 min-w-[56px] h-12 transition-all active:scale-95 ${
                isActive
                  ? 'text-primary-container font-semibold'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              <span className="material-symbols-outlined text-[22px]">
                {item.icon}
              </span>
              <span className="font-label-sm text-[10px] tracking-tight">
                {item.shortLabel}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
