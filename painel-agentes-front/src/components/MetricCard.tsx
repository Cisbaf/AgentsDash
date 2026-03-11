import {
    Box,
    Flex,
    Text,
    Center,
    Card
} from "@chakra-ui/react";

export default function MetricCard({ title, value, icon, bgIcon }: { title: string; value: string | number; icon: React.ReactNode; bgIcon: string }) {
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