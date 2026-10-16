import React from 'react';
import { useTheme } from '../context/ThemeContext';
import type { HealthResponse } from '../services/api';

interface HeaderProps {
  activeScreenTitle: string;
  health: HealthResponse | null;
  healthLoading: boolean;
  onRefreshHealth: () => void;
  isSidebarCollapsed?: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  activeScreenTitle,
  health,
  healthLoading,
  onRefreshHealth,
  isSidebarCollapsed = false,
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
    <header
      className={`fixed top-0 left-0 ${
        isSidebarCollapsed ? 'md:left-20' : 'md:left-64'
      } right-0 z-30 pt-safe bg-surface/90 backdrop-blur-xl border-b border-outline-variant/30 transition-all duration-200`}
    >
      <div className="h-16 px-4 md:px-8 flex items-center justify-between gap-4 max-w-6xl mx-auto">
        {/* Mobile View: Logo + Title */}
        <div className="flex items-center gap-2.5 min-w-0 md:hidden">
          <div className="w-8 h-8 rounded-lg bg-primary-container/25 border border-primary-container/50 flex items-center justify-center flex-shrink-0">
            <span className="material-symbols-outlined text-primary text-[20px]">
              grain
            </span>
          </div>
          <div className="flex flex-col min-w-0">
            <div className="flex items-center gap-1.5">
              <span className="font-headline-sm text-sm font-semibold tracking-tight text-primary">
                Chittix
              </span>
              <span className="font-code-sm text-[9px] uppercase px-1 py-0.2 rounded bg-surface-container-high text-secondary border border-secondary/20">
                {health?.graph || 'company_brain'}
              </span>
            </div>
            <span className="font-label-sm text-[11px] text-on-surface-variant font-medium truncate">
              {activeScreenTitle}
            </span>
          </div>
        </div>

        {/* Desktop View: Breadcrumb & Context */}
        <div className="hidden md:flex items-center gap-3 min-w-0">
          <div className="flex items-center gap-2 font-headline-sm text-sm">
            <span className="text-on-surface-variant font-medium">Chittix</span>
            <span className="text-outline text-xs">/</span>
            <span className="text-primary font-semibold tracking-tight">
              {activeScreenTitle}
            </span>
          </div>
          <span className="text-outline-variant text-xs">•</span>
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-surface-container border border-outline-variant/30 text-xs font-code-sm text-on-surface-variant">
            <span>GRAPH:</span>
            <span className="font-mono text-secondary font-semibold" title={health?.graph || 'company_brain'}>
              {health?.graph || 'company_brain'}
            </span>
          </div>
        </div>

        {/* Action Controls: Live Status & Mobile-Only Theme Toggle (No duplicate on Desktop) */}
        <div className="flex items-center gap-2 flex-shrink-0">
          <button
            onClick={onRefreshHealth}
            title="Re-check backend and FalkorDB connectivity"
            className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high border border-outline-variant/30 text-xs font-label-sm text-on-surface-variant hover:text-primary transition-all active:scale-95 cursor-pointer"
          >
            <span
              className={`w-2 h-2 rounded-full flex-shrink-0 ${healthStatusColor}`}
            />
            <span className="font-medium hidden sm:inline">
              {`FalkorDB ${healthStatusText}`}
            </span>
            <span className="material-symbols-outlined text-[14px] text-outline hover:text-primary">
              refresh
            </span>
          </button>

          {/* Theme toggle only rendered here for mobile; desktop uses sidebar toggle to eliminate duplicate */}
          <button
            onClick={toggleTheme}
            aria-label="Toggle theme"
            title={`Switch to ${theme === 'dark' ? 'Light' : 'Dark'} mode`}
            className="md:hidden w-9 h-9 flex items-center justify-center rounded-lg border border-outline-variant/30 bg-surface-container hover:bg-surface-container-high text-on-surface-variant hover:text-primary transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">
              {theme === 'dark' ? 'light_mode' : 'dark_mode'}
            </span>
          </button>
        </div>
      </div>
    </header>
  );
};
