export type ScreenId = 'pulse' | 'map' | 'consequence' | 'ask' | 'sources' | 'integrations';

export interface NavItem {
  id: ScreenId;
  label: string;
  shortLabel: string;
  icon: string;
}

export const NAV_ITEMS: NavItem[] = [
  { id: 'pulse', label: 'Company Pulse', shortLabel: 'Pulse', icon: 'insights' },
  { id: 'map', label: 'Knowledge Map', shortLabel: 'Map', icon: 'hub' },
  { id: 'consequence', label: 'Consequence Explorer', shortLabel: 'Consequence', icon: 'alt_route' },
  { id: 'ask', label: 'Ask & Investigate', shortLabel: 'Investigate', icon: 'troubleshoot' },
  { id: 'sources', label: 'GitHub Sources', shortLabel: 'Sources', icon: 'deployed_code' },
  { id: 'integrations', label: 'Integrations & Admin', shortLabel: 'Admin', icon: 'settings_ethernet' },
];

