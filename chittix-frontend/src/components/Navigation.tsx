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
  isCollapsed: boolean;
  onToggleCollapse: () => void;
}

export const DesktopSidebar: React.FC<DesktopSidebarProps> = ({
  activeScreen,
  onSelectScreen,
  health,
  healthLoading,
  onRefreshHealth,
  isCollapsed,
  onToggleCollapse,
}) => {
  const { theme, toggleTheme } = useTheme();
  const isGraphOnline = health?.falkordb === 'UP';

  const healthStatusText = healthLoading
    ? 'Checking...'
    : isGraphOnline
    ? 'Online'
    : 'Offline';

  const healthStatusColor = healthLoading
    ? 'bg-amber-400 animate-pulse'
    : isGraphOnline
    ? 'bg-tertiary animate-pulse'
    : 'bg-error';

  return (
    <aside
      className={`hidden md:flex flex-col h-screen fixed left-0 top-0 bg-surface-container-low border-r border-outline-variant/30 z-40 select-none transition-all duration-200 ${
        isCollapsed ? 'w-20' : 'w-64'
      }`}
    >
      {/* Branding & Collapse Toggle */}
      <div className="p-3.5 border-b border-outline-variant/30 flex flex-col gap-2">
        <div className={`flex items-center ${isCollapsed ? 'justify-center flex-col gap-2' : 'justify-between'}`}>
          <div className="flex items-center gap-2.5 min-w-0">
            <div
              className="w-8 h-8 rounded-lg bg-primary-container/25 border border-primary-container/50 flex items-center justify-center flex-shrink-0"
              title="Chittix Company Intelligence"
            >
              <span className="material-symbols-outlined text-primary text-[20px]">
                grain
              </span>
            </div>
            {!isCollapsed && (
              <div className="flex flex-col min-w-0">
                <span className="font-headline-sm text-base font-bold tracking-tight text-primary">
                  Chittix
                </span>
                <span className="font-code-sm text-[10px] text-secondary tracking-wider uppercase font-semibold">
                  Company Intelligence
                </span>
              </div>
            )}
          </div>

          <button
            onClick={onToggleCollapse}
            title={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className="w-7 h-7 flex items-center justify-center rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface-variant hover:text-primary transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">
              {isCollapsed ? 'chevron_right' : 'chevron_left'}
            </span>
          </button>
        </div>

        {!isCollapsed && (
          <div className="mt-1 px-2.5 py-1 rounded-md bg-surface-container border border-outline-variant/30 flex items-center justify-between text-[11px] font-code-sm">
            <span className="text-on-surface-variant">GRAPH:</span>
            <span className="text-secondary font-mono font-semibold truncate max-w-[130px]" title={health?.graph || 'company_brain'}>
              {health?.graph || 'company_brain'}
            </span>
          </div>
        )}
      </div>

      {/* Nav List */}
      <nav className="flex-1 px-2.5 py-4 flex flex-col gap-1.5 overflow-y-auto">
        {!isCollapsed && (
          <div className="px-2 pb-1 text-[10px] font-code-sm uppercase tracking-wider text-outline font-semibold">
            Intelligence Views
          </div>
        )}
        {NAV_ITEMS.map(item => {
          const isActive = activeScreen === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onSelectScreen(item.id)}
              title={isCollapsed ? item.label : undefined}
              className={`w-full flex items-center ${
                isCollapsed ? 'justify-center px-0 py-2.5' : 'gap-3 px-3 py-2.5'
              } rounded-xl font-label-md transition-all text-left cursor-pointer ${
                isActive
                  ? 'bg-surface-container-high text-primary font-semibold shadow-sm border-l-2 border-primary-container'
                  : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
              }`}
            >
              <span
                className={`material-symbols-outlined text-[20px] transition-colors ${
                  isActive ? 'text-primary' : 'text-on-surface-variant'
                }`}
              >
                {item.icon}
              </span>
              {!isCollapsed && <span className="truncate text-sm">{item.label}</span>}
            </button>
          );
        })}
      </nav>

      {/* Footer Controls: Shared Theme Toggle & Backend Health Status */}
      <div className={`p-3 border-t border-outline-variant/30 flex flex-col gap-2.5 bg-surface-container-lowest ${isCollapsed ? 'items-center' : ''}`}>
        {/* Theme Toggle Button */}
        <button
          onClick={toggleTheme}
          title={isCollapsed ? `Switch to ${theme === 'dark' ? 'Light' : 'Dark'} mode` : undefined}
          className={`${
            isCollapsed ? 'w-10 h-10 justify-center p-0' : 'w-full px-3 py-2 justify-between'
          } flex items-center rounded-lg bg-surface-container hover:bg-surface-container-high border border-outline-variant/20 text-on-surface text-xs font-label-sm transition-colors cursor-pointer`}
        >
          <span className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[16px] text-secondary">
              {theme === 'dark' ? 'dark_mode' : 'light_mode'}
            </span>
            {!isCollapsed && <span className="capitalize font-medium">{theme} Mode</span>}
          </span>
          {!isCollapsed && (
            <span className="font-code-sm text-[10px] text-outline uppercase font-semibold">
              Toggle
            </span>
          )}
        </button>

        {/* Backend Health telemetry badge */}
        <div className={`flex items-center ${isCollapsed ? 'justify-center' : 'justify-between px-1'} text-[11px] font-code-sm w-full`}>
          <div className="flex items-center gap-1.5" title={`FalkorDB Graph Cluster: ${healthStatusText}`}>
            <span className={`w-2 h-2 rounded-full ${healthStatusColor}`} />
            {!isCollapsed && (
              <span className="text-on-surface-variant font-medium">
                {`FalkorDB ${healthStatusText}`}
              </span>
            )}
          </div>
          {!isCollapsed && (
            <button
              onClick={onRefreshHealth}
              title="Re-check backend and FalkorDB connectivity"
              className="text-secondary hover:underline cursor-pointer font-medium"
            >
              check
            </button>
          )}
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
    <nav className="md:hidden fixed bottom-0 w-full z-50 pb-safe bg-surface/95 backdrop-blur-xl border-t border-outline-variant/30 shadow-[0_-4px_20px_rgba(0,0,0,0.15)] dark:shadow-[0_-4px_20px_rgba(0,0,0,0.45)]">
      <div className="h-16 px-2 flex items-center justify-around max-w-md mx-auto">
        {NAV_ITEMS.map(item => {
          const isActive = activeScreen === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onSelectScreen(item.id)}
              className={`flex flex-col items-center justify-center gap-0.5 min-w-[56px] h-12 transition-all active:scale-95 cursor-pointer ${
                isActive
                  ? 'text-primary font-semibold'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              <span className={`material-symbols-outlined text-[22px] ${isActive ? 'text-secondary' : ''}`}>
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
