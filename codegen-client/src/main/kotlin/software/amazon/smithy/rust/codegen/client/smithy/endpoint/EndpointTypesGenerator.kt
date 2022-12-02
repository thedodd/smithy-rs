/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.client.smithy.endpoint

import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameters
import software.amazon.smithy.rulesengine.traits.EndpointTestCase
import software.amazon.smithy.rust.codegen.client.smithy.ClientCodegenContext
import software.amazon.smithy.rust.codegen.client.smithy.endpoint.generators.EndpointParamsGenerator
import software.amazon.smithy.rust.codegen.client.smithy.endpoint.generators.EndpointResolverGenerator
import software.amazon.smithy.rust.codegen.client.smithy.endpoint.generators.EndpointTestGenerator
import software.amazon.smithy.rust.codegen.core.rustlang.Writable
import software.amazon.smithy.rust.codegen.core.smithy.RuntimeType

/**
 * Entrypoint for Endpoints 2.0 Code generation
 *
 * This exposes [RuntimeType]s for the individual components of endpoints 2.0
 */
class EndpointTypesGenerator(
    codegenContext: ClientCodegenContext,
    private val rules: EndpointRuleSet,
    private val tests: List<EndpointTestCase>,
) {
    val params: Parameters = rules.parameters
    private val runtimeConfig = codegenContext.runtimeConfig
    private val customizations = codegenContext.rootDecorator.endpointCustomizations(codegenContext)
    private val stdlib = customizations
        .flatMap { it.customRuntimeFunctions(codegenContext) }

    companion object {
        fun fromContext(codegenContext: ClientCodegenContext): EndpointTypesGenerator? {
            val index = EndpointRulesetIndex.of(codegenContext.model)
            val rulesOrNull = index.endpointRulesForService(codegenContext.serviceShape)
            return rulesOrNull?.let { rules ->
                EndpointTypesGenerator(codegenContext, rules, index.endpointTests(codegenContext.serviceShape))
            }
        }
    }

    fun paramsStruct(): RuntimeType = EndpointParamsGenerator(params).paramsStruct()
    fun defaultResolver(): RuntimeType = EndpointResolverGenerator(stdlib, runtimeConfig).defaultEndpointResolver(rules)
    fun testGenerator(): Writable =
        EndpointTestGenerator(tests, paramsStruct(), defaultResolver(), params, runtimeConfig).generate()

    /**
     * Load the builtIn value for [parameter] from the endpoint customizations. If the built-in comes from service config,
     * [config] refers to `&crate::config::Config`
     *
     * Exactly one endpoint customization must provide the value for this builtIn or null is returned.
     */
    fun builtInFor(parameter: Parameter, config: String): Writable? {
        val defaultProviders = customizations
            .mapNotNull { it.builtInDefaultValue(parameter, config) }
        if (defaultProviders.size > 1) {
            error("Multiple providers provided a value for the builtin $parameter")
        }
        return defaultProviders.firstOrNull()
    }
}