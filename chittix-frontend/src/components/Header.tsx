import React from 'react';
import { useTheme } from '../context/ThemeContext';
import type { HealthResponse } from '../services/api';

interface HeaderProps {
  activeScreenTitle: string;
  health: HealthResponse | null;
  healthLoading: boolean;
  onRefreshHealth: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  activeScreenTitle,
  health,
  healthLoading,
  onRefreshHealth,
}) => {
  const { theme, toggleTheme } = useTheme();

  const isGraphOnline = health?.falkordb === 'UP';

  return (
    <header className="fixed top-0 w-full z-40 pt-safe bg-surface-dim/80 backdrop-blur-xl border-b border-outline-variant/20">
      <div className="h-16 px-gutter flex items-center justify-between gap-space-sm max-w-7xl mx-auto">
        <div className="flex items-center gap-space-sm min-w-0">
          <div className="w-8 h-8 rounded-lg bg-primary-container/20 border border-primary-container/40 flex items-center justify-center flex-shrink-0">
            <span className="material-symbols-outlined text-primary-container text-[20px]">
              grain
            </span>
          </div>

          <div className="flex flex-col min-w-0">
            <div className="flex items-center gap-space-xs">
              <span className="font-headline-sm text-headline-sm font-semibold tracking-tight text-primary">
                Chittix
              </span>
              <span className="font-code-sm text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-surface-container-high text-secondary border border-secondary/20 truncate">
                {health?.graph || 'company_brain'}
              </span>
            </div>
            <div className="flex items-center gap-space-xs">
              <span className="font-headline-sm text-[11px] text-on-surface-variant font-medium truncate">
                {activeScreenTitle}
              </span>
              <span className="text-[10px] text-outline-variant">•</span>
              <button
                onClick={onRefreshHealth}
                title="Click to re-check backend health"
                className="inline-flex items-center gap-1 font-label-sm text-[10px] text-tertiary-container hover:opacity-80 transition-opacity"
              >
                <span
                  className={`w-1.5 h-1.5 rounded-full ${
                    isGraphOnline ? 'bg-tertiary-container animate-pulse' : 'bg-error'
                  }`}
                />
                {healthLoading
                  ? 'Checking...'
                  : isGraphOnline
                  ? 'Graph Online'
                  : 'FalkorDB Standby'}
              </button>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-space-xs flex-shrink-0">
          <button
            onClick={toggleTheme}
            aria-label="Toggle theme"
            title={`Switch to ${theme === 'dark' ? 'Light' : 'Dark'} mode`}
            className="w-10 h-10 flex items-center justify-center rounded-xl text-on-surface-variant hover:text-primary hover:bg-surface-container transition-colors"
          >
            <span className="material-symbols-outlined text-[20px]">
              {theme === 'dark' ? 'light_mode' : 'dark_mode'}
            </span>
          </button>
        </div>
      </div>
    </header>
  );
};
