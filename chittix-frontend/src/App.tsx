import React, { useState, useEffect, useCallback } from 'react';
import { ThemeProvider } from './context/ThemeContext';
import { Header } from './components/Header';
import { DesktopSidebar, MobileBottomNav } from './components/Navigation';
import { NAV_ITEMS, type ScreenId } from './types/navigation';
import { CompanyPulse } from './components/CompanyPulse';
import { KnowledgeMap } from './components/KnowledgeMap';
import { ConsequenceExplorer } from './components/ConsequenceExplorer';
import { AskInvestigate } from './components/AskInvestigate';
import {
  checkHealth,
  getPaymentsContext,
  getProviderStatus,
  type HealthResponse,
  type PaymentsContextResponse,
  type ProviderStatusResponse,
} from './services/api';

export const AppContent: React.FC = () => {
  const [activeScreen, setActiveScreen] = useState<ScreenId>('pulse');
  const [askInitialQuery, setAskInitialQuery] = useState<string>('');

  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState<boolean>(() => {
    return localStorage.getItem('chittix-sidebar-collapsed') === 'true';
  });

  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [healthLoading, setHealthLoading] = useState<boolean>(true);

  const [paymentsContext, setPaymentsContext] = useState<PaymentsContextResponse | null>(null);
  const [loadingContext, setLoadingContext] = useState<boolean>(true);

  const [providerStatus, setProviderStatus] = useState<ProviderStatusResponse | null>(null);
  const [providerStatusLoading, setProviderStatusLoading] = useState<boolean>(true);

  const toggleSidebarCollapse = useCallback(() => {
    setIsSidebarCollapsed(prev => {
      const next = !prev;
      localStorage.setItem('chittix-sidebar-collapsed', String(next));
      return next;
    });
  }, []);

  const refreshHealth = useCallback(async () => {
    setHealthLoading(true);
    try {
      const res = await checkHealth();
      setHealth(res);
    } catch {
      setHealth({
        application: 'DOWN',
        falkordb: 'DOWN',
        graph: 'company_brain',
        message: 'Backend or FalkorDB unreachable at localhost:8080',
      });
    } finally {
      setHealthLoading(false);
    }
  }, []);

  const refreshContext = useCallback(async () => {
    setLoadingContext(true);
    try {
      const res = await getPaymentsContext();
      setPaymentsContext(res);
    } catch {
      setPaymentsContext(null);
    } finally {
      setLoadingContext(false);
    }
  }, []);

  const refreshProviderStatus = useCallback(async () => {
    setProviderStatusLoading(true);
    try {
      const res = await getProviderStatus();
      setProviderStatus(res);
    } catch {
      setProviderStatus(null);
    } finally {
      setProviderStatusLoading(false);
    }
  }, []);

  const refreshAllData = useCallback(() => {
    refreshHealth();
    refreshContext();
    refreshProviderStatus();
  }, [refreshHealth, refreshContext, refreshProviderStatus]);

  useEffect(() => {
    refreshHealth();
    refreshContext();
    refreshProviderStatus();
  }, [refreshHealth, refreshContext, refreshProviderStatus]);

  const handleNavigateToAsk = (query?: string) => {
    if (query) {
      setAskInitialQuery(query);
    }
    setActiveScreen('ask');
  };

  const activeTitle = NAV_ITEMS.find(n => n.id === activeScreen)?.label || 'Company Pulse';

  return (
    <div className="min-h-screen bg-surface text-on-surface font-body-md flex">
      {/* Shared Desktop Sidebar Navigation */}
      <DesktopSidebar
        activeScreen={activeScreen}
        onSelectScreen={setActiveScreen}
        health={health}
        healthLoading={healthLoading}
        onRefreshHealth={refreshHealth}
        isCollapsed={isSidebarCollapsed}
        onToggleCollapse={toggleSidebarCollapse}
      />

      {/* Main App Content Viewport */}
      <div
        className={`flex-1 flex flex-col ${
          isSidebarCollapsed ? 'md:pl-20' : 'md:pl-64'
        } min-w-0 transition-all duration-200`}
      >
        <Header
          activeScreenTitle={activeTitle}
          health={health}
          healthLoading={healthLoading}
          onRefreshHealth={refreshHealth}
          isSidebarCollapsed={isSidebarCollapsed}
        />

        <main className="flex-1 pt-20 pb-20 md:pb-8">
          {activeScreen === 'pulse' && (
            <CompanyPulse
              health={health}
              healthLoading={healthLoading}
              paymentsContext={paymentsContext}
              loadingContext={loadingContext}
              providerStatus={providerStatus}
              providerStatusLoading={providerStatusLoading}
              onRefreshData={refreshAllData}
              onNavigateToAsk={handleNavigateToAsk}
            />
          )}

          {activeScreen === 'map' && (
            <KnowledgeMap
              paymentsContext={paymentsContext}
              loading={loadingContext}
              onRefresh={refreshContext}
              onNavigateToAsk={handleNavigateToAsk}
            />
          )}

          {activeScreen === 'consequence' && (
            <ConsequenceExplorer
              paymentsContext={paymentsContext}
              loading={loadingContext}
              onRefresh={refreshContext}
              onNavigateToAsk={handleNavigateToAsk}
            />
          )}

          {activeScreen === 'ask' && (
            <AskInvestigate
              initialQuestion={askInitialQuery}
              providerStatus={providerStatus}
            />
          )}
        </main>
      </div>

      {/* Shared Mobile Bottom Navigation */}
      <MobileBottomNav
        activeScreen={activeScreen}
        onSelectScreen={setActiveScreen}
      />
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <ThemeProvider>
      <AppContent />
    </ThemeProvider>
  );
};

export default App;
