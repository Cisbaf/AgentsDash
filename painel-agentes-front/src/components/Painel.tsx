import { useEffect, useMemo, useRef, useState } from "react";
import {
    Box, Flex, Grid, Text, Heading, Spinner, Center, Card,
    Table, Button, IconButton, Stack
} from "@chakra-ui/react";
import { PhoneCall, PhoneMissed, Users, Headset, ListFilter, Info, Clock } from "lucide-react";
import { GlobalMetrics, AgentStatus } from "../types";
import MetricCard from "./MetricCard";
import CallsDialogs from "./CallsDialogs";
import PauseDialog from "./PauseDialog";

export default function PainelAgentes() {
    const [globalMetrics, setGlobalMetrics] = useState<GlobalMetrics>();
    const [agents, setAgents] = useState<AgentStatus[]>([]);
    const [loading, setLoading] = useState(true);

    // Estados para o modal principal (ligações + pausas)
    const [pauses, setPauses] = useState<any[]>([]);
    const [pausesLoading, setPausesLoading] = useState(false);
    const [isDialogOpen, setIsDialogOpen] = useState(false);
    const [selectedAgent, setSelectedAgent] = useState<AgentStatus | null>(null);

    // Estados para o novo modal exclusivo de pausas
    const [isPauseDialogOpen, setIsPauseDialogOpen] = useState(false);
    const [selectedAgentForPauses, setSelectedAgentForPauses] = useState<AgentStatus | null>(null);
    const [pauseHistory, setPauseHistory] = useState<any[]>([]);
    const [pauseHistoryLoading, setPauseHistoryLoading] = useState(false);

    // Filtros
    const [roleFilters, setRoleFilters] = useState<string[]>([]);
    const [isFilterMenuOpen, setIsFilterMenuOpen] = useState(false);

    const filteredAgents = useMemo(() => {
        const filtered = roleFilters.length > 0
            ? agents.filter(agent => agent.agentRole && roleFilters.includes(agent.agentRole) && agent.tempoTotalLigacaoSegundos > 0)
            : agents.filter(agent => agent.agentRole != null && agent.agentRole !== "" && agent.tempoTotalLigacaoSegundos > 0);
        return [...filtered].sort((a, b) => (a.agentRole || '').localeCompare(b.agentRole || ''));
    }, [agents, roleFilters]);

    const isFetching = useRef(false);

    const fetchDashboardData = async () => {
        if (isFetching.current) return;
        isFetching.current = true;
        try {
            const [metricsRes, agentsRes] = await Promise.all([
                fetch("/api/metrics/global"),
                fetch("/api/metrics/agents"),
            ]);
            if (!metricsRes.ok) throw new Error(`Erro HTTP: ${metricsRes.status}`);
            const metricsData = await metricsRes.json();
            if (!agentsRes.ok) throw new Error(`Erro HTTP: ${agentsRes.status}`);
            const agentsData = await agentsRes.json();
            setGlobalMetrics(metricsData);
            const agentsArray = Object.keys(agentsData).map((key) => ({
                ...agentsData[key],
                id: key,
            }));
            setAgents(
                agentsArray
                    .filter(a => a.nomeAgente && a.nomeAgente !== "")
                    .sort((a, b) => a.nomeAgente.localeCompare(b.nomeAgente))
            );
        } catch (err) {
            console.error("Erro ao carregar dados:", err);
        } finally {
            isFetching.current = false;
            setLoading(false);
        }
    };

    // Busca pausas para o modal principal (com ligações)
    const fetchPausas = async () => {
        if (!selectedAgent?.id) return;
        setPausesLoading(true);
        try {
            const pausesRes = await fetch(`/api/agents/pauses/${selectedAgent.id}`);
            if (!pausesRes.ok) throw new Error(`Erro HTTP: ${pausesRes.status}`);
            const pausesData = await pausesRes.json();
            setPauses(pausesData);
        } catch (err) {
            console.error("Erro ao carregar pausas:", err);
            setPauses([]);
        } finally {
            setPausesLoading(false);
        }
    };

    // Busca pausas para o modal exclusivo
    const fetchPauseHistory = async (agentId: string) => {
        setPauseHistoryLoading(true);
        try {
            const res = await fetch(`/api/agents/pauses/${agentId}`);
            if (!res.ok) throw new Error(`Erro HTTP: ${res.status}`);
            const data = await res.json();
            setPauseHistory(data);
        } catch (err) {
            console.error("Erro ao carregar pausas:", err);
            setPauseHistory([]);
        } finally {
            setPauseHistoryLoading(false);
        }
    };

    const handleOpenDetails = (agent: AgentStatus) => {
        setSelectedAgent(agent);
        setIsDialogOpen(true);
        fetchPausas();
    };

    const handleOpenPauseDetails = (agent: AgentStatus) => {
        setSelectedAgentForPauses(agent);
        setIsPauseDialogOpen(true);
        fetchPauseHistory(agent.id!);
    };

    const toggleRoleFilter = (role: string) => {
        setRoleFilters(prev =>
            prev.includes(role)
                ? prev.filter(r => r !== role)
                : [...prev, role]
        );
    };

    useEffect(() => {
        fetchDashboardData();
        const interval = setInterval(fetchDashboardData, 1000);
        return () => clearInterval(interval);
    }, []);

    const formatTime = (seconds: number) => {
        if (!seconds) return "00:00";
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);
        return `${h > 0 ? `${h.toString().padStart(2, "0")}:` : ""}${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
    };

    const getLiveTime = (accumulatedSeconds: number, lastChangeTimestamp: string | null, isActive: boolean) => {
        if (!isActive || !lastChangeTimestamp) return accumulatedSeconds;

        try {
            // lastChangeTimestamp pode ser ISO ("2026-03-30T14:35:20") ou apenas hora ("14:35:20")
            let timePart: string;
            if (lastChangeTimestamp.includes('T')) {
                // Formato ISO: pega depois do 'T'
                timePart = lastChangeTimestamp.split('T')[1];
            } else {
                timePart = lastChangeTimestamp;
            }

            const [hours, minutes, seconds] = timePart.split(':').map(Number);
            if (isNaN(hours)) return accumulatedSeconds;

            const now = new Date();
            const lastChange = new Date();
            lastChange.setHours(hours, minutes, seconds, 0);

            const diffInSeconds = Math.floor((now.getTime() - lastChange.getTime()) / 1000);
            if (diffInSeconds < 0 || diffInSeconds > 86400) return accumulatedSeconds;

            return accumulatedSeconds + diffInSeconds;
        } catch (e) {
            return accumulatedSeconds;
        }
    };

    if (loading) {
        return (
            <Center h="100vh" bg="gray.50">
                <Flex direction="column" align="center" gap="4">
                    <Spinner size="xl" color="blue.500" />
                    <Text fontWeight="medium" color="gray.600">Carregando métricas da operação...</Text>
                </Flex>
            </Center>
        );
    }

    return (
        <Box minH="100vh" bg="gray.800" p={{ base: "4", md: "8" }} color="white">
            <Box mt="7vh">
                <Box as="header" position="absolute" top={0} left={0} right={0} bg="red.800" p="4" zIndex="sticky" boxShadow="md" display="flex" alignItems="center" justifyContent="center">
                    <Heading size="xl" color="white">PAINEL DE INFORMAÇÕES DOS AGENTES</Heading>
                    <img src="logo.png" alt="Logo" width={150} style={{ marginLeft: '20px' }} />
                </Box>

                {globalMetrics && (
                    <Grid templateColumns={{ base: "1fr", md: "repeat(4, 1fr)" }} gap="3" mb="8">
                        <MetricCard title="Total Chamadas" value={globalMetrics.totalchamadasRecebidas} icon={<PhoneCall size={24} color="#3182CE" />} bgIcon="blue.50" />
                        <MetricCard title="Atendidas" value={globalMetrics.totalChamadasAtendidas} icon={<Headset size={24} color="#38A169" />} bgIcon="green.50" />
                        <MetricCard title="Abandonadas" value={globalMetrics.chamadasAbandonadas} icon={<PhoneMissed size={24} color="#E53E3E" />} bgIcon="red.50" />
                        <MetricCard title="Em Fila" value={globalMetrics.chamadasEmFila} icon={<Users size={24} color="#D69E2E" />} bgIcon="yellow.50" />
                    </Grid>
                )}

                <Card.Root bg="white" borderRadius="xl" overflow="hidden">
                    <Flex p="6" borderBottomWidth="1px" borderColor="gray.100" justifyContent="space-between" alignItems="center">
                        <Heading size="md" color="gray.800">Status dos Agentes</Heading>
                        <Box position="relative">
                            <Box
                                as="button"
                                p={2}
                                borderRadius="md"
                                _hover={{ bg: "gray.100" }}
                                color={roleFilters.length > 0 ? "blue.500" : "gray.600"}
                                onClick={() => setIsFilterMenuOpen(!isFilterMenuOpen)}
                                title="Filtrar por função"
                            >
                                <ListFilter size={20} />
                            </Box>
                            {isFilterMenuOpen && (
                                <>
                                    <Box
                                        position="fixed" top={0} left={0} right={0} bottom={0}
                                        zIndex="99"
                                        onClick={() => setIsFilterMenuOpen(false)}
                                    />
                                    <Card.Root
                                        position="absolute" top="100%" right={0} mt="2" minW="220px"
                                        zIndex="100" boxShadow="xl" p="4" bg="white" border="1px solid" borderColor="gray.200"
                                    >
                                        <Stack gap="3">
                                            <Text fontSize="sm" fontWeight="bold" color="gray.700" borderBottomWidth="1px" pb="2">
                                                Filtrar Funções
                                            </Text>
                                            <Stack gap="2" maxH="200px" overflowY="auto">
                                                {[...new Set(agents.map(a => a.agentRole).filter(Boolean))].map(role => (
                                                    <Flex as="label" key={role} align="center" gap="2" cursor="pointer" _hover={{ bg: "gray.50" }} p="1" borderRadius="md">
                                                        <input
                                                            type="checkbox"
                                                            checked={roleFilters.includes(role)}
                                                            onChange={() => toggleRoleFilter(role)}
                                                            style={{ cursor: "pointer" }}
                                                        />
                                                        <Text fontSize="sm" color="gray.700" userSelect="none">{role}</Text>
                                                    </Flex>
                                                ))}
                                            </Stack>
                                            {roleFilters.length > 0 && (
                                                <Button size="xs" variant="surface" colorPalette="red" onClick={() => setRoleFilters([])} mt="2" w="full">
                                                    Limpar Filtros
                                                </Button>
                                            )}
                                        </Stack>
                                    </Card.Root>
                                </>
                            )}
                        </Box>
                    </Flex>

                    <Box overflowX="auto">
                        <Table.Root variant="line" size="sm">
                            <Table.Header bg="gray.50">
                                <Table.Row>
                                    <Table.ColumnHeader color="gray.800" fontWeight="700" letterSpacing="0.1em" py="2" pl="4">Agente</Table.ColumnHeader>
                                    <Table.ColumnHeader color="gray.800" fontWeight="700" letterSpacing="0.1em" py="2">Função</Table.ColumnHeader>
                                    <Table.ColumnHeader color="blue.600" fontWeight="700" letterSpacing="0.1em" py="2">Recebidas</Table.ColumnHeader>
                                    <Table.ColumnHeader color="yellow.600" fontWeight="700" letterSpacing="0.1em" py="2">Pausas</Table.ColumnHeader>
                                    <Table.ColumnHeader color="orange.700" fontWeight="700" letterSpacing="0.1em" py="2">Tempo Pausa</Table.ColumnHeader>
                                    <Table.ColumnHeader color="green.700" fontWeight="700" letterSpacing="0.1em" py="2">Tempo Ligação</Table.ColumnHeader>
                                    <Table.ColumnHeader color="purple.700" fontWeight="700" letterSpacing="0.1em" py="2">Tempo Livre</Table.ColumnHeader>
                                    <Table.ColumnHeader color="red.700" fontWeight="700" letterSpacing="0.1em" py="2">Removidos</Table.ColumnHeader>
                                    <Table.ColumnHeader color="gray.800" fontWeight="700" letterSpacing="0.1em" py="2">Ações</Table.ColumnHeader>
                                </Table.Row>
                            </Table.Header>
                            <Table.Body>
                                {filteredAgents.map((agent) => {
                                    const isOnCall = agent.ultimoStatusRamal === 1;
                                    const isOnPause = agent.ultimoStatusAcd === 5;
                                    const isRinging = agent.ultimoStatusRamal === 8;
                                    const isFree = agent.ultimoStatusRamal === 0 && !isOnPause;

                                    const statusColor = isOnCall ? "red.700" : isRinging ? "yellow.600" : isOnPause ? "gray.500" : isFree ? "green.800" : "gray.500";
                                    const statusDot = isOnCall ? "●" : isRinging ? "◉" : isOnPause ? "○" : "●";

                                    const liveLigacao = getLiveTime(agent.tempoTotalLigacaoSegundos, agent.mudancaRamal, isOnCall);
                                    const livePausa = getLiveTime(agent.tempoTotalPausaSegundos, agent.mudancaRamal, isOnPause);
                                    const liveLivre = getLiveTime(agent.tempoTotalLivreSegundos, agent.mudancaRamal, isFree);

                                    return (
                                        <Table.Row key={agent.id} _hover={{ bg: "gray.100" }} _even={{ bg: "gray.50" }} _odd={{ bg: "white" }}>
                                            <Table.Cell py="1.5" pl="4">
                                                <Flex align="center" gap="2">
                                                    <Text as="span" color={statusColor} fontSize="30px" fontWeight="700" title={isOnCall ? "Em ligação" : isRinging ? "Tocando" : isOnPause ? "Em pausa" : "Livre"}>
                                                        {statusDot}
                                                    </Text>
                                                    <Text fontSize="18px" fontWeight="600" letterSpacing="0.03em" whiteSpace="nowrap">
                                                        {agent.nomeAgente.toUpperCase()}
                                                    </Text>
                                                </Flex>
                                            </Table.Cell>
                                            <Table.Cell py="1.5">
                                                <Text fontSize="16px" color="gray.800" fontWeight="600">{agent.agentRole}</Text>
                                            </Table.Cell>
                                            <Table.Cell py="1.5" textAlign="center">
                                                <Text fontSize="20px" fontWeight="700" color="blue.600" fontFamily="mono">{agent.chamadasRecebidasTotal}</Text>
                                            </Table.Cell>
                                            <Table.Cell py="1.5" textAlign="center" cursor="pointer" onClick={() => handleOpenPauseDetails(agent)}>
                                                <Text fontSize="20px" fontWeight="700" color="yellow.600" fontFamily="mono" _hover={{ textDecoration: "underline" }}>
                                                    {agent.pausasIniciadasTotal}
                                                </Text>
                                            </Table.Cell>
                                            <Table.Cell py="1.5" textAlign="center" cursor="pointer" onClick={() => handleOpenPauseDetails(agent)}>
                                                <Text fontSize="20px" fontWeight="600" color={livePausa > 0 ? "orange.700" : "gray.800"} fontFamily="mono" _hover={{ textDecoration: "underline" }}>
                                                    {formatTime(livePausa)}
                                                </Text>
                                            </Table.Cell>
                                            <Table.Cell py="1.5" textAlign="center">
                                                <Text fontSize="20px" fontWeight="600" color={liveLigacao > 0 ? "green.700" : "gray.800"} fontFamily="mono">
                                                    {formatTime(liveLigacao)}
                                                </Text>
                                            </Table.Cell>
                                            <Table.Cell py="1.5" textAlign="center">
                                                <Text fontSize="20px" fontWeight="600" color={liveLivre > 0 ? "purple.700" : "gray.800"} fontFamily="mono">
                                                    {formatTime(liveLivre)}
                                                </Text>
                                            </Table.Cell>
                                            <Table.Cell py="1.5" textAlign="center">
                                                {agent.removido > 0
                                                    ? <Text fontSize="20px" fontWeight="700" color="red.700" fontFamily="mono">{agent.removido}</Text>
                                                    : <Text fontSize="20px" fontWeight="700" color="red.700" fontFamily="mono">0</Text>
                                                }
                                            </Table.Cell>
                                            <Table.Cell py="1.5" pr="4" textAlign="center">
                                                <IconButton aria-label="Ver detalhes" variant="ghost" size="xs" color="gray.500" _hover={{ color: "blue.300", bg: "gray.700" }} onClick={() => handleOpenDetails(agent)}>
                                                    <Info size={14} />
                                                </IconButton>
                                            </Table.Cell>
                                        </Table.Row>
                                    );
                                })}
                            </Table.Body>
                        </Table.Root>
                    </Box>
                </Card.Root>
            </Box>

            {/* Modal principal (ligações + pausas) */}
            <CallsDialogs isDialogOpen={isDialogOpen} setIsDialogOpen={setIsDialogOpen} selectedAgent={selectedAgent} />

            {/* Novo modal exclusivo para pausas */}
            <PauseDialog isPauseDialogOpen={isPauseDialogOpen} setIsPauseDialogOpen={setIsPauseDialogOpen} selectedAgentForPauses={selectedAgentForPauses} pauseHistoryLoading={pauseHistoryLoading} pauseHistory={pauseHistory} />
        </Box>
    );
}