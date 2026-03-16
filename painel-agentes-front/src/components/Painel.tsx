import { useEffect, useMemo, useRef, useState } from "react";
import {
    Box,
    Flex,
    Grid,
    Text,
    Heading,
    Badge,
    Spinner,
    Center,
    Card,
    Table,
    NativeSelect,
    Button,
    DialogRoot,
    DialogContent,
    DialogHeader,
    DialogBody,
    DialogFooter,
    DialogCloseTrigger,
    DialogTitle,
    DialogBackdrop,
    IconButton,
    Stack,
    Separator,
    Portal,
} from "@chakra-ui/react";
import { PhoneCall, PhoneMissed, Users, Headset, ListFilter, Info, Clock } from "lucide-react";
import { GlobalMetrics, AgentStatus, Ligacoes, FirstLastCalls } from "../types";
import MetricCard from "./MetricCard";

export default function PainelAgentes() {
    const [globalMetrics, setGlobalMetrics] = useState<GlobalMetrics>();
    const [agents, setAgents] = useState<AgentStatus[]>([]);
    const [loading, setLoading] = useState(true);
    const [roleFilter, setRoleFilter] = useState("");

    // Estados para o Modal (Dialog)
    const [isDialogOpen, setIsDialogOpen] = useState(false);
    const [selectedAgent, setSelectedAgent] = useState<AgentStatus | null>(null);
    const [agentCalls, setAgentCalls] = useState<Ligacoes[]>([]);
    const [firstLastCalls, setFirstLastCalls] = useState<FirstLastCalls | null>(null);
    const [loadingDetails, setLoadingDetails] = useState(false);

    const filteredAgents = useMemo(() => {
        if (!roleFilter) return agents;
        return agents.filter(agent =>
            agent.agentRole?.toLowerCase() === roleFilter.toLowerCase() || agent.nomeAgente.toLowerCase().includes(roleFilter.toLowerCase())
        );
    }, [agents, roleFilter]);

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
            console.log(agentsArray);
            setAgents(agentsArray.filter(a => a.nomeAgente !== "" || a.nomeAgente !== null).sort((a, b) => a.nomeAgente.localeCompare(b.nomeAgente)));;
        } catch (err) {
            console.error("Erro ao carregar dados:", err);
        } finally {
            isFetching.current = false;
            setLoading(false);
        }
    };

    const fetchAgentDetails = async (agentId: string) => {
        setLoadingDetails(true);
        try {
            const [callsRes, datesRes] = await Promise.all([
                fetch(`/api/metrics/ligacao/${agentId}`),
                fetch(`/api/metrics/ligacao/dates/${agentId}`)
            ]);
            const callsData = callsRes.ok ? await callsRes.json() : [];
            const datesData = datesRes.ok ? await datesRes.json() : null;
            setAgentCalls(callsData);
            setFirstLastCalls(datesData);
        } catch (err) {
            console.error("Erro ao carregar detalhes:", err);
            setAgentCalls([]);
            setFirstLastCalls(null);
        } finally {
            setLoadingDetails(false);
        }
    };

    const handleOpenDetails = (agent: AgentStatus) => {
        setSelectedAgent(agent);
        setIsDialogOpen(true);
        if (agent.id) fetchAgentDetails(agent.id);
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
                            <Box as="button" p={2} borderRadius="md" _hover={{ bg: "gray.100" }} color={roleFilter ? "blue.500" : "gray.600"}>
                                <ListFilter size={20} />
                            </Box>
                            <NativeSelect.Root position="absolute" top={0} left={0} w="100%" h="100%" opacity={0}>
                                <NativeSelect.Field cursor="pointer" onChange={(e) => setRoleFilter(e.target.value)} value={roleFilter}>
                                    <option value="">Todas as Funções</option>
                                    {[...new Set(agents.map(a => a.agentRole).filter(Boolean))].map(role => (
                                        <option key={role} value={role}>{role}</option>
                                    ))}
                                </NativeSelect.Field>
                            </NativeSelect.Root>
                        </Box>
                    </Flex>
                    <Box overflowX="auto">
                        <Table.Root variant="line">
                            <Table.Header bg="gray.50">
                                <Table.Row>
                                    <Table.ColumnHeader color="blue.800">Agente</Table.ColumnHeader>
                                    <Table.ColumnHeader color="purple.800">Função</Table.ColumnHeader>
                                    <Table.ColumnHeader color="green.800">Recebidas</Table.ColumnHeader>
                                    <Table.ColumnHeader color="orange.900">Tempo Pausa</Table.ColumnHeader>
                                    <Table.ColumnHeader color="yellow.700">Tempo Ligação</Table.ColumnHeader>
                                    <Table.ColumnHeader color="teal.700">Removidos</Table.ColumnHeader>
                                    <Table.ColumnHeader color="teal.700">Ações</Table.ColumnHeader>
                                </Table.Row>
                            </Table.Header>
                            <Table.Body>
                                {filteredAgents.map((agent) => (
                                    <Table.Row key={agent.id} _hover={{ bg: "gray.50" }}>
                                        <Table.Cell fontWeight="bold" color="gray.700">{agent.nomeAgente.toUpperCase()}</Table.Cell>
                                        <Table.Cell color="gray.700">{agent.agentRole}</Table.Cell>
                                        <Table.Cell><Badge colorPalette="blue" variant="subtle">{agent.chamadasRecebidasTotal}</Badge></Table.Cell>
                                        <Table.Cell color="gray.700">{formatTime(agent.tempoTotalPausaSegundos)}</Table.Cell>
                                        <Table.Cell color="gray.700">{formatTime(agent.tempoTotalLigacaoSegundos)}</Table.Cell>
                                        <Table.Cell><Badge colorPalette="red" variant="subtle">{agent.removido}</Badge></Table.Cell>
                                        <Table.Cell>
                                            <IconButton
                                                aria-label="Ver detalhes"
                                                variant="ghost"
                                                colorPalette="blue"
                                                onClick={() => handleOpenDetails(agent)}
                                            >
                                                <Info size={18} />
                                            </IconButton>
                                        </Table.Cell>
                                    </Table.Row>
                                ))}
                            </Table.Body>
                        </Table.Root>
                    </Box>
                </Card.Root>
            </Box>

            {/* Modal (Dialog) v3 Corrigido */}
            <DialogRoot
                open={isDialogOpen}
                onOpenChange={(e) => setIsDialogOpen(e.open)}
                size="lg"
                placement="center"
                motionPreset="slide-in-bottom"
            >
                <Portal>
                    <DialogBackdrop bg="blackAlpha.600" />
                    <DialogContent
                        bg="white"
                        color="gray.800"
                        borderRadius="xl"
                        boxShadow="2xl"
                        position="fixed"
                        top="50%"
                        left="50%"
                        transform="translate(-50%, -50%)"
                        zIndex="modal"
                    >
                        <DialogHeader borderBottomWidth="1px" py="4">
                            <DialogTitle fontSize="lg">Detalhamento: {selectedAgent?.nomeAgente}</DialogTitle>
                        </DialogHeader>
                        <DialogCloseTrigger color="gray.800" top="4" right="4" />
                        <DialogBody py="6">
                            {loadingDetails ? (
                                <Center py="10"><Spinner /></Center>
                            ) : (
                                <Stack gap="6">
                                    {firstLastCalls && (
                                        <Box p="4" bg="blue.50" borderRadius="lg" borderLeft="4px solid" borderColor="blue.500">
                                            <Heading size="xs" mb="3" color="blue.800" display="flex" alignItems="center">
                                                <Clock size={14} style={{ marginRight: '6px' }} /> Resumo da Operação
                                            </Heading>
                                            <Grid templateColumns="1fr 1fr" gap="4">
                                                <Box>
                                                    <Text fontSize="xs" fontWeight="bold" color="gray.500">PRIMEIRA LIGAÇÃO</Text>
                                                    <Text fontSize="sm" fontWeight="medium">
                                                        {firstLastCalls.frist ? new Date(firstLastCalls.frist.timestamp).toLocaleString('pt-BR') : "N/A"}
                                                    </Text>
                                                </Box>
                                                <Box>
                                                    <Text fontSize="xs" fontWeight="bold" color="gray.500">ÚLTIMA LIGAÇÃO</Text>
                                                    <Text fontSize="sm" fontWeight="medium">
                                                        {firstLastCalls.last ? new Date(firstLastCalls.last.timestamp).toLocaleString('pt-BR') : "N/A"}
                                                    </Text>
                                                </Box>
                                            </Grid>
                                        </Box>
                                    )}
                                    <Separator />
                                    <Box>
                                        <Heading size="xs" mb="3" color="gray.700">Histórico Completo</Heading>
                                        <Box maxH="300px" overflowY="auto">
                                            <Table.Root variant="line" size="sm">
                                                <Table.Header bg="gray.50">
                                                    <Table.Row>
                                                        <Table.ColumnHeader fontWeight={"bold"}>Nº Telefone</Table.ColumnHeader>
                                                        <Table.ColumnHeader fontWeight={"bold"}>Horário</Table.ColumnHeader>
                                                    </Table.Row>
                                                </Table.Header>
                                                <Table.Body>
                                                    {agentCalls.length > 0 ? agentCalls.map((call, i) => (
                                                        <Table.Row key={i}>
                                                            <Table.Cell>{call.callerIdRAni}</Table.Cell>
                                                            <Table.Cell>{new Date(call.timestamp).toLocaleString('pt-BR')}</Table.Cell>
                                                        </Table.Row>
                                                    )) : (
                                                        <Table.Row><Table.Cell colSpan={2} textAlign="center">Nenhum registro.</Table.Cell></Table.Row>
                                                    )}
                                                </Table.Body>
                                            </Table.Root>
                                        </Box>
                                    </Box>
                                </Stack>
                            )}
                        </DialogBody>
                        <DialogFooter borderTopWidth="1px" py="3">
                            <Button onClick={() => setIsDialogOpen(false)} colorPalette="blue" variant="solid">Fechar</Button>
                        </DialogFooter>
                    </DialogContent>
                </Portal>
            </DialogRoot>
        </Box>
    );
}