"use client"
import { ChakraProvider, defaultSystem } from "@chakra-ui/react"

export function Provider(props: any) {
    return (
        <ChakraProvider value={defaultSystem}>
            {props.children}
        </ChakraProvider>
    )
}