/*
 * Copyright 2026 Mario Lichtsinn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.lichtsinn.ageris.spring;

import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;
import java.util.Map;

/**
 * The condition behind {@link ConditionalOnFiles}. It binds the list of files, so that it sees a list however it is
 * written: comma-separated in a properties file, or as a list in YAML.
 */
final class OnFilesCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        final Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnFiles.class.getName());
        if (attributes == null || !(attributes.get("value") instanceof String[] managers)) {
            return false;
        }
        final Binder binder = Binder.get(context.getEnvironment());
        for (final String manager : managers) {
            final BindResult<List<String>> files = binder.bind("ageris." + manager + ".files", Bindable.listOf(String.class));
            if (files.isBound() && !files.get().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
