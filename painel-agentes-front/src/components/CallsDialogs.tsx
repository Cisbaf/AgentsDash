import {
    Box,
    Center,
    Spinner,
    Stack,
    Heading,
    Grid,
    Text,
    Separator,
    Table,
    Button,
    DialogRoot,
    DialogContent,
    DialogHeader,
    DialogBody,
    DialogFooter,
    DialogCloseTrigger,
    DialogTitle,
    DialogBackdrop,
    Portal,
} from "@chakra-ui/react";
import { Clock } from "lucide-react";
import { AgentStatus, Ligacoes, FirstLastCalls } from "../types";

interface CallsDialogsProps {
    isDialogOpen: boolean;
    setIsDialogOpen: (open: boolean) => void;
    selectedAgent: AgentStatus | null;

}

export default function CallsDialogs({
    isDialogOpen,
    setIsDialogOpen,
    selectedAgent
}: CallsDialogsProps) {
    return (
        <DialogRoot open={isDialogOpen} onOpenChange={(e) => setIsDialogOpen(e.open)} size="lg" placement="center" motionPreset="slide-in-bottom">
            <Portal>
                <DialogBackdrop bg="blackAlpha.600" />
                <DialogContent bg="white" color="gray.800" borderRadius="xl" boxShadow="2xl" position="fixed" top="50%" left="50%" transform="translate(-50%, -50%)" zIndex="modal">
                    <DialogHeader borderBottomWidth="1px" py="4">
                        <DialogTitle fontSize="lg">Detalhamento: {selectedAgent?.nomeAgente}</DialogTitle>
                    </DialogHeader>
                    <DialogCloseTrigger color="gray.800" top="4" right="4" />
                    <DialogBody py="6">
                        <Stack gap="6">
                            {selectedAgent && selectedAgent.ligacoes.length > 0 && (() => {
                                const sorted = [...selectedAgent.ligacoes].sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
                                return (
                                    <Box p="4" bg="blue.50" borderRadius="lg" borderLeft="4px solid" borderColor="blue.500">
                                        <Heading size="xs" mb="3" color="blue.800" display="flex" alignItems="center">
                                            <Clock size={14} style={{ marginRight: '6px' }} /> Resumo da Operação
                                        </Heading>
                                        <Grid templateColumns="1fr 1fr" gap="4">
                                            <Box>
                                                <Text fontSize="xs" fontWeight="bold" color="gray.500">PRIMEIRA LIGAÇÃO</Text>
                                                <Text fontSize="sm" fontWeight="medium">{new Date(sorted[0].timestamp).toLocaleString('pt-BR')}</Text>
                                            </Box>
                                            <Box>
                                                <Text fontSize="xs" fontWeight="bold" color="gray.500">ÚLTIMA LIGAÇÃO</Text>
                                                <Text fontSize="sm" fontWeight="medium">{new Date(sorted[sorted.length - 1].timestamp).toLocaleString('pt-BR')}</Text>
                                            </Box>
                                        </Grid>
                                    </Box>
                                );
                            })()}
                            <Separator />
                            <Box>
                                <Heading size="xs" mb="3" color="gray.700">Histórico de Ligações</Heading>
                                <Box maxH="300px" overflowY="auto">
                                    <Table.Root variant="line" size="sm">
                                        <Table.Header bg="gray.50">
                                            <Table.Row>
                                                <Table.ColumnHeader fontWeight="bold">Nº Telefone</Table.ColumnHeader>
                                                <Table.ColumnHeader fontWeight="bold">Horário</Table.ColumnHeader>
                                            </Table.Row>
                                        </Table.Header>
                                        <Table.Body>
                                            {selectedAgent && selectedAgent.ligacoes.length > 0
                                                ? [...selectedAgent.ligacoes]
                                                    .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
                                                    .map((call, i) => (
                                                        <Table.Row key={i}>
                                                            <Table.Cell>{call.callerIdRAni}</Table.Cell>
                                                            <Table.Cell>{new Date(call.timestamp).toLocaleString('pt-BR')}</Table.Cell>
                                                        </Table.Row>
                                                    ))
                                                : <Table.Row><Table.Cell colSpan={2} textAlign="center">Nenhum registro.</Table.Cell></Table.Row>
                                            }
                                        </Table.Body>
                                    </Table.Root>
                                </Box>
                            </Box>
                            <Separator />
                        </Stack>
                    </DialogBody>
                    <DialogFooter borderTopWidth="1px" py="3">
                        <Button onClick={() => setIsDialogOpen(false)} colorPalette="blue" variant="solid">Fechar</Button>
                    </DialogFooter>
                </DialogContent>
            </Portal>
        </DialogRoot>
    )
}