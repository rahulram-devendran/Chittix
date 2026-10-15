export interface HealthResponse {
  application: string;
  falkordb: string;
  graph: string;
  message: string;
}

export interface DecisionContext {
  decision: string;
  project: string;
  system: string;
  ownerTeam: string;
  documentTitle: string;
  sourceId: string;
  evidencePath: string[];
}

export interface PaymentsContextResponse {
  graph: string;
  count: number;
  contexts: DecisionContext[];
}

export interface Evidence {
  documentTitle: string;
  sourceId: string;
}

export interface AskResponse {
  question: string;
  answered: boolean;
  answerType: string;
  answer: string;
  facts: string[];
  decisions: DecisionContext[];
  evidence: Evidence[];
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export async function checkHealth(): Promise<HealthResponse> {
  const res = await fetch(`${BASE_URL}/api/health`);
  if (!res.ok) {
    // 503 still returns HealthResponse JSON with falkordb: "DOWN"
    try {
      const data = await res.json();
      return data;
    } catch {
      throw new Error(`Health check failed with HTTP ${res.status}`);
    }
  }
  return res.json();
}

export async function getPaymentsContext(): Promise<PaymentsContextResponse> {
  const res = await fetch(`${BASE_URL}/api/demo/payments-context`);
  if (!res.ok) {
    const errorText = await res.text().catch(() => '');
    throw new Error(`Failed to fetch payments context: HTTP ${res.status} ${errorText}`);
  }
  return res.json();
}

export async function askQuestion(question: string): Promise<AskResponse> {
  const res = await fetch(`${BASE_URL}/api/brain/ask`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ question }),
  });
  if (!res.ok) {
    const errorText = await res.text().catch(() => '');
    throw new Error(`Question request failed: HTTP ${res.status} ${errorText}`);
  }
  return res.json();
}
