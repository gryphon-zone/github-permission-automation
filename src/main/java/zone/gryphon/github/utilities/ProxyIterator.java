/*
 * Copyright 2019-2019 Gryphon Zone
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zone.gryphon.github.utilities;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Iterator;
import java.util.function.Function;

@RequiredArgsConstructor
public class ProxyIterator<T, R> implements Iterator<R> {

    @NonNull
    private final Iterator<T> wrapped;

    @NonNull
    private final Function<T, R> function;

    @Override
    public boolean hasNext() {
        return wrapped.hasNext();
    }

    @Override
    public R next() {
        return function.apply(wrapped.next());
    }

    @Override
    public void remove() {
        wrapped.remove();
    }
}
