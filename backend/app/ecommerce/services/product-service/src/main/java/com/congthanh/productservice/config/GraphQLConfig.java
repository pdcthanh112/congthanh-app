package com.congthanh.productservice.config;

//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import graphql.GraphQL;
//import graphql.schema.GraphQLSchema;
//import graphql.schema.idl.RuntimeWiring;
//import graphql.schema.idl.SchemaGenerator;
//import graphql.schema.idl.SchemaParser;
//import graphql.schema.idl.TypeDefinitionRegistry;
//import org.springframework.util.StreamUtils;
//import org.springframework.web.bind.annotation.RestController;
//import javax.annotation.PostConstruct;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.core.io.Resource;
//import graphql.GraphQL;
//import graphql.schema.idl.RuntimeWiring;
//
//@Configuration
public class GraphQLConfig {
//
//    @Autowired
//    private GraphQLDataFetcher dataFetcher;
//
//    @Bean
//    public GraphQL graphQL() {
//        Resource schemaResource = new ClassPathResource("schema.graphqls");
//        String sdl;
//        try {
//            sdl = StreamUtils.copyToString(schemaResource.getInputStream(), StandardCharsets.UTF_8);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        GraphQLSchema schema = buildSchema(sdl);
//        return GraphQL.newGraphQL(schema).build();
//    }
//
//    private GraphQLSchema buildSchema(String sdl) {
//        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
//        RuntimeWiring wiring = buildWiring();
//        SchemaGenerator schemaGenerator = new SchemaGenerator();
//        return schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
//    }
//
////    private RuntimeWiring buildWiring() {
////        return RuntimeWiring.newRuntimeWiring()
////                .type("Query", typeWiring -> typeWiring.dataFetcher("getBookById", dataFetcher.getBookByIdDataFetcher()))
////                .type("Query", typeWiring -> typeWiring.dataFetcher("getAllBooks", dataFetcher.getAllBooksDataFetcher()))
////                .type("Mutation", typeWiring -> typeWiring.dataFetcher("createBook", dataFetcher.createBookDataFetcher()))
////                .build();
////    }
}
