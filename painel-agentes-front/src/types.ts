
export type GlobalMetrics = {
    totalChamadasAtendidas: number;
    totalchamadasRecebidas: number;
    totalChamadasEmFila: number;
    chamadasAbandonadas: number;
    totalTempoToqueSegundosGlobal: number;
    chamadasEmFila: number;
};

export type AgentStatus = {
    id?: string;
    nomeAgente: string;
    agentRole: string;
    chamadasAtendidasTotal: number;
    chamadasRecebidasTotal: number;
    pausasIniciadasTotal: number;
    tempoTotalPausaSegundos: number;
    tempoTotalLigacaoSegundos: number;
    tempoTotalLivreSegundos: number;
    tempoTotalToqueSegundos: number;
    removido: number;
    ultimoStatusAcd: number;
    ultimoStatusRamal: number;
    ringingStartTime: string | null;
    ligacoes: Ligacoes[];
    mudancaRamal: any;
};

export type Ligacoes = {
    callerIdRAni: string;
    timestamp: string;
}

export type FirstLastCalls = {
    frist?: Ligacoes;
    last?: Ligacoes;
};