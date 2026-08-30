import javassist.bytecode.AnnotationDefaultAttribute
import japicmp.model.JApiCompatibilityChangeType

// Adding a defaulted annotation member is compatible for existing compiled and
// source declarations, but japicmp classifies it as an abstract method addition.
jApiClasses.each { apiClass ->
    if (apiClass.fullyQualifiedName ==
            'io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse') {
        apiClass.methods.findAll { method ->
            method.name == 'semanticRead' && method.parameters.empty
        }.each { method ->
            method.compatibilityChanges.findAll { change ->
                change.type == JApiCompatibilityChangeType.METHOD_ABSTRACT_ADDED_TO_CLASS
            }.each { change ->
                def newMethod = method.newMethod.orElseThrow {
                    new IllegalStateException('semanticRead compatibility change has no new method')
                }
                if (newMethod.methodInfo2.getAttribute(AnnotationDefaultAttribute.tag) == null) {
                    throw new IllegalStateException(
                            'semanticRead compatibility exception requires an annotation default')
                }
                change.binaryCompatible = true
                change.sourceCompatible = true
            }
        }
    }
}

return jApiClasses
