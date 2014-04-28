import org.consulo.lombok.annotations.LazyInstance;

public class LazyInstanceMethod
{
	@LazyInstance
	public String getInstance()
	{
		return com.intellij.openapi.components.ServiceManager.getService(String.class);
	}

	@LazyInstance
	public static String getStaticInstance()
	{
		return com.intellij.openapi.components.ServiceManager.getService(String.class);
	}

	@LazyInstance(notNull = false)
	public String getNullableInstance()
	{
		return com.intellij.openapi.components.ServiceManager.getService(String.class);
	}

	@LazyInstance(notNull = false)
	public static String getNullableStaticInstance()
	{
		return com.intellij.openapi.components.ServiceManager.getService(String.class);
	}
}