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
import { AgentStatus } from "../types";

interface PauseDialogProps {
    isPauseDialogOpen: boolean;
    setIsPauseDialogOpen: (open: boolean) => void;
    selectedAgentForPauses: AgentStatus | null;
    pauseHistoryLoading: boolean;
    pauseHistory: any[];

}

export default function PauseDialog({
    isPauseDialogOpen,
    setIsPauseDialogOpen,
    selectedAgentForPauses,
    pauseHistoryLoading,
    pauseHistory
}: PauseDialogProps) {
    return (
        <DialogRoot open={isPauseDialogOpen} onOpenChange={(e) => setIsPauseDialogOpen(e.open)} size="lg" placement="center" motionPreset="slide-in-bottom">
            <Portal>
                <DialogBackdrop bg="blackAlpha.600" />
                <DialogContent bg="white" color="gray.800" borderRadius="xl" boxShadow="2xl" position="fixed" top="50%" left="50%" transform="translate(-50%, -50%)" zIndex="modal">
                    <DialogHeader borderBottomWidth="1px" py="4">
                        <DialogTitle fontSize="lg">Histórico de Pausas: {selectedAgentForPauses?.nomeAgente}</DialogTitle>
                    </DialogHeader>
                    <DialogCloseTrigger color="gray.800" top="4" right="4" />
                    <DialogBody py="6">
                        <Stack gap="6">
                            <Box>
                                <Heading size="xs" mb="3" color="gray.700">Registros de Pausa</Heading>
                                {pauseHistoryLoading ? (
                                    <Center py="4">
                                        <Spinner size="sm" />
                                    </Center>
                                ) : (
                                    <Box maxH="400px" overflowY="auto">
                                        <Table.Root variant="line" size="sm">
                                            <Table.Header bg="gray.50">
                                                <Table.Row>
                                                    <Table.ColumnHeader fontWeight="bold">Tipo</Table.ColumnHeader>
                                                    <Table.ColumnHeader fontWeight="bold">Duração</Table.ColumnHeader>
                                                    <Table.ColumnHeader fontWeight="bold">Data/Hora</Table.ColumnHeader>
                                                </Table.Row>
                                            </Table.Header>
                                            <Table.Body>
                                                {pauseHistory.length > 0 ? (
                                                    pauseHistory.map((pause, idx) => (
                                                        <Table.Row key={idx}>
                                                            <Table.Cell fontWeight={"bold"}>{pause.type || pause.tipo || '-'}</Table.Cell>
                                                            <Table.Cell>{pause.duration || pause.duracao || '-'}</Table.Cell>
                                                            <Table.Cell>{pause.date || pause.data || '-'}</Table.Cell>
                                                        </Table.Row>
                                                    ))
                                                ) : (
                                                    <Table.Row>
                                                        <Table.Cell colSpan={3} textAlign="center">Nenhuma pausa registrada.</Table.Cell>
                                                    </Table.Row>
                                                )}
                                            </Table.Body>
                                        </Table.Root>
                                    </Box>
                                )}
                            </Box>
                        </Stack>
                    </DialogBody>
                    <DialogFooter borderTopWidth="1px" py="3">
                        <Button onClick={() => setIsPauseDialogOpen(false)} colorPalette="blue" variant="solid">Fechar</Button>
                    </DialogFooter>
                </DialogContent>
            </Portal>
        </DialogRoot>
    )
}