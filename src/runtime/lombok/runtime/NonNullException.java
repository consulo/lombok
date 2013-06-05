/*
 * Copyright 2013 Consulo.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lombok.runtime;

/**
 * @author VISTALL
 * @since 20:03/04.06.13
 */
public class NonNullException extends NullPointerException
{
	public NonNullException(String s)
	{
		super(s);

		final StackTraceElement[] stackTrace = getStackTrace();
		if(stackTrace.length > 0)
		{
			StackTraceElement[] newElements = new StackTraceElement[stackTrace.length - 1];
			System.arraycopy(stackTrace, 1, newElements, 0, stackTrace.length - 1);
			setStackTrace(newElements);
		}
	}

	public static <T> T throwIfNull(T value)
	{
		if(value == null)
		{
			throw new NonNullException("Result is null");
		}
		return value;
	}
}
