"use client";

import { useEffect, useRef, useState } from "react";
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
  Table
} from "@chakra-ui/react";
import { PhoneCall, PhoneMissed, Clock, Users, Headset } from "lucide-react";
import { GlobalMetrics, AgentStatus } from "../types";

export default function Home() {
  const [globalMetrics, setGlobalMetrics] = useState<GlobalMetrics | null>(null);
  const [agents, setAgents] = useState<AgentStatus[]>([]);
  const [loading, setLoading] = useState(true);

  const isFetching = useRef(false);

  const fetchDashboardData = async () => {
    if (isFetching.current) return;

    isFetching.current = true;
    try {
      const [metricsRes, agentsRes] = await Promise.all([
        fetch("/api/metrics/global"),
        fetch("/api/metrics/agents"),
      ]);

      // Verifica se a resposta foi bem-sucedida (status 2xx)
      if (!metricsRes.ok) {
        throw new Error(`Erro HTTP: ${metricsRes.status}`);
      }
      // Verifica se há conteúdo antes de converter para JSON
      const metricsText = await metricsRes.text();
      const metricsData = metricsText ? JSON.parse(metricsText) : {};

      if (!agentsRes.ok) {
        throw new Error(`Erro HTTP: ${agentsRes.status}`);
      }
      const agentsText = await agentsRes.text();
      const agentsData = agentsText ? JSON.parse(agentsText) : {};

      setGlobalMetrics(metricsData);

      const agentsArray = Object.keys(agentsData).map((key) => ({
        ...agentsData[key],
        id: key,
      }));

      agentsArray.sort((a, b) => a.nomeAgente.localeCompare(b.nomeAgente));
      setAgents(agentsArray);
    } catch (err) {
      console.error("Erro ao carregar dados:", err);
    } finally {
      isFetching.current = false;
      setLoading(false);
    }
  };

  useEffect(() => {
    // Executa a primeira vez imediatamente
    fetchDashboardData();

    // Define o intervalo de 1 segundo (1000ms)
    const interval = setInterval(() => {
      fetchDashboardData();
    }, 1000);

    // Limpa o intervalo quando o usuário sai da página
    return () => clearInterval(interval);
  }, []);

  const formatTime = (seconds: number) => {
    if (!seconds) return "00:00";
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    return `${h > 0 ? h.toString().padStart(2, "0") + ":" : ""}${m
      .toString()
      .padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  };

  if (loading) {
    return (
      <Center h="100vh" bg="gray.50">
        <Flex direction="column" align="center" gap="4">
          <Spinner size="xl" color="blue.500" />
          <Text fontWeight="medium" color="gray.600">
            Carregando métricas da operação...
          </Text>
        </Flex>
      </Center>
    );
  }

  return (
    <Box minH="100vh" bg="gray.800" p={{ base: "4", md: "8" }} color="white" >

      <Box mt="7vh">
        {/* Cabeçalho */}
        <Box
          as="header"
          position="absolute"
          top={0}
          left={0}
          right={0}
          bg="red.800"
          p="4"
          zIndex="sticky"
          boxShadow="md"
          borderBottomWidth="1px"
          borderBottomColor="gray.700"
          borderRadius="sm"
          display="flex"
          alignItems="center"
          justifyContent="center"
          flexDirection="row"
        >
          <Heading size="xl" textAlign="center" color="white">
            PAINEL DE INFORMAÇÕES DOS AGENTES
          </Heading>
          <img src="logo.png" alt="Cisbaf" width={150} height={150} />
        </Box>


        {/* Cards de Métricas Globais */}
        {globalMetrics && (
          <Grid
            templateColumns={{ base: "1fr", md: "repeat(2, 1fr)", lg: "repeat(4, 1fr)" }}
            gap="3"
            mb="8"
          >
            <MetricCard
              title="Total de Chamadas"
              value={globalMetrics.totalchamadasRecebidas}
              icon={<PhoneCall size={24} color="#3182CE" />}
              bgIcon="blue.50"
            />
            <MetricCard
              title="Atendidas"
              value={globalMetrics.totalChamadasAtendidas}
              icon={<Headset size={24} color="#38A169" />}
              bgIcon="green.50"
            />
            <MetricCard
              title="Abandonadas"
              value={globalMetrics.chamadasAbandonadas}
              icon={<PhoneMissed size={24} color="#E53E3E" />}
              bgIcon="red.50"
            />
            <MetricCard
              title="Em Fila"
              value={globalMetrics.chamadasEmFila}
              icon={<Users size={24} color="#D69E2E" />}
              bgIcon="yellow.50"
            />

          </Grid>
        )}

        {/* Tabela de Agentes */}
        <Card.Root bg="white" shadow="sm" borderRadius="xl" overflow="hidden">
          <Box p="6" borderBottomWidth="1px" borderColor="gray.100">
            <Heading size="md" color="gray.800">Status dos Agentes</Heading>
          </Box>
          <Box overflowX="auto">
            <Table.Root variant="line" size="md">
              <Table.Header bg="gray.50">
                <Table.Row>
                  <Table.ColumnHeader color="blue.800">Agente</Table.ColumnHeader>
                  <Table.ColumnHeader color="purple.800">Função</Table.ColumnHeader>
                  <Table.ColumnHeader color="green.800">Recebidas</Table.ColumnHeader>
                  <Table.ColumnHeader color="orange.700">Pausas</Table.ColumnHeader>
                  <Table.ColumnHeader color="orange.900">Tempo Pausa</Table.ColumnHeader>
                  <Table.ColumnHeader color="yellow.700">Tempo em Ligação</Table.ColumnHeader>
                  <Table.ColumnHeader color="teal.700">Tempo Livre</Table.ColumnHeader>
                  <Table.ColumnHeader color="red.700">Removidos</Table.ColumnHeader>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {agents.toSorted((a, b) => a.agentRole?.localeCompare(b.agentRole)).map((agent) => (
                  <Table.Row key={agent.id} _hover={{ bg: "gray.50" }}>
                    <Table.Cell fontWeight="medium">{agent.nomeAgente.toUpperCase()}</Table.Cell>
                    <Table.Cell>{agent.agentRole}</Table.Cell>

                    <Table.Cell>
                      <Badge colorPalette="blue" variant="subtle" rounded="full" px="2">
                        {agent.chamadasRecebidasTotal}
                      </Badge>
                    </Table.Cell>
                    <Table.Cell>{agent.pausasIniciadasTotal}</Table.Cell>
                    <Table.Cell>{formatTime(agent.tempoTotalPausaSegundos)}</Table.Cell>
                    <Table.Cell>{formatTime(agent.tempoTotalLigacaoSegundos)}</Table.Cell>
                    <Table.Cell>{formatTime(agent.tempoTotalLivreSegundos)}</Table.Cell>
                    <Table.Cell>{agent.removido}</Table.Cell>
                  </Table.Row>
                ))}
                {agents.length === 0 && (
                  <Table.Row>
                    <Table.Cell colSpan={6} textAlign="center" py="8" color="gray.500">
                      Nenhum agente encontrado no momento.
                    </Table.Cell>
                  </Table.Row>
                )}
              </Table.Body>
            </Table.Root>
          </Box>
        </Card.Root>

      </Box>
    </Box>
  );
}

// Componente Card Auxiliar usando Chakra v3
function MetricCard({ title, value, icon, bgIcon }: { title: string; value: string | number; icon: React.ReactNode; bgIcon: string }) {
  return (
    <Card.Root shadow="sm" borderRadius="xl">
      <Card.Body>
        <Flex align="center" gap="4">
          <Center p="3" bg={bgIcon} borderRadius="lg">
            {icon}
          </Center>
          <Box>
            <Text fontSize="sm" fontWeight="medium" color="gray.500">
              {title}
            </Text>
            <Text fontSize="2xl" fontWeight="bold" color="gray.900" mt="0.5">
              {value}
            </Text>
          </Box>
        </Flex>
      </Card.Body>
    </Card.Root>
  );
}