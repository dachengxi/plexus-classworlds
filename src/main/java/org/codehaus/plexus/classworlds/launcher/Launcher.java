package org.codehaus.plexus.classworlds.launcher;

/*
 * Copyright 2001-2006 Codehaus Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;

/**
 * <p>Command-line invokable application launcher.</p>
 * 
 * <p>This launcher class assists in the creation of classloaders and <code>ClassRealm</code>s
 * from a configuration file and the launching of the application's <code>main</code>
 * method from the correct class loaded through the correct classloader.</p>
 * 
 * <p> The path to the configuration file is specified using the <code>classworlds.conf</code>
 * system property, typically specified using the <code>-D</code> switch to
 * <code>java</code>.</p>
 *
 * @author <a href="mailto:bob@eng.werken.com">bob mcwhirter</a>
 *
 * plexus-classworlds的启动类
 */
public class Launcher
{
    protected static final String CLASSWORLDS_CONF = "classworlds.conf";

    protected static final String UBERJAR_CONF_DIR = "WORLDS-INF/conf/";

    protected ClassLoader systemClassLoader;

    protected String mainClassName;

    protected String mainRealmName;

    protected ClassWorld world;

    private int exitCode = 0;

    public Launcher()
    {
        this.systemClassLoader = Thread.currentThread().getContextClassLoader();
    }

    public void setSystemClassLoader( ClassLoader loader )
    {
        this.systemClassLoader = loader;
    }

    public ClassLoader getSystemClassLoader()
    {
        return this.systemClassLoader;
    }

    public int getExitCode()
    {
        return exitCode;
    }

    public void setAppMain( String mainClassName,
                            String mainRealmName )
    {
        this.mainClassName = mainClassName;

        this.mainRealmName = mainRealmName;
    }

    public String getMainRealmName()
    {
        return this.mainRealmName;
    }

    public String getMainClassName()
    {
        return this.mainClassName;
    }

    public void setWorld( ClassWorld world )
    {
        this.world = world;
    }

    public ClassWorld getWorld()
    {
        return this.world;
    }

    /**
     * Configure from a file.
     *
     * @param is The config input stream.
     * @throws IOException             If an error occurs reading the config file.
     * @throws MalformedURLException   If the config file contains invalid URLs.
     * @throws ConfigurationException  If the config file is corrupt.
     * @throws org.codehaus.plexus.classworlds.realm.DuplicateRealmException If the config file defines two realms
     *                                 with the same id.
     * @throws org.codehaus.plexus.classworlds.realm.NoSuchRealmException    If the config file defines a main entry
     *                                 point in a non-existent realm.
     *
     * 读取配置文件，并进行配置
     */
    public void configure( InputStream is )
        throws IOException, ConfigurationException, DuplicateRealmException, NoSuchRealmException
    {
        // 配置器
        Configurator configurator = new Configurator( this );

        // 读取配置文件进行配置
        configurator.configure( is );
    }

    /**
     * Retrieve the main entry class.
     *
     * @return The main entry class.
     * @throws ClassNotFoundException If the class cannot be found.
     * @throws NoSuchRealmException   If the specified main entry realm does not exist.
     */
    public Class<?> getMainClass()
        throws ClassNotFoundException, NoSuchRealmException
    {
        return getMainRealm().loadClass( getMainClassName() );
    }

    /**
     * Retrieve the main entry realm.
     *
     * @return The main entry realm.
     * @throws NoSuchRealmException If the specified main entry realm does not exist.
     */
    public ClassRealm getMainRealm()
        throws NoSuchRealmException
    {
        return getWorld().getRealm( getMainRealmName() );
    }

    /**
     * Retrieve the enhanced main entry method.
     *
     * @return The enhanced main entry method.
     * @throws ClassNotFoundException If the main entry class cannot be found.
     * @throws NoSuchMethodException  If the main entry method cannot be found.
     * @throws NoSuchRealmException   If the main entry realm cannot be found.
     */
    protected Method getEnhancedMainMethod()
        throws ClassNotFoundException, NoSuchMethodException, NoSuchRealmException
    {
        Class<?> cwClass = getMainRealm().loadClass( ClassWorld.class.getName() );

        Method m = getMainClass().getMethod( "main", String[].class, cwClass );

        int modifiers = m.getModifiers();

        if ( Modifier.isStatic( modifiers ) && Modifier.isPublic( modifiers ) )
        {
            if ( m.getReturnType() == Integer.TYPE || m.getReturnType() == Void.TYPE )
            {
                return m;
            }
        }

        throw new NoSuchMethodException( "public static void main(String[] args, ClassWorld world)" );
    }

    /**
     * Retrieve the main entry method.
     *
     * @return The main entry method.
     * @throws ClassNotFoundException If the main entry class cannot be found.
     * @throws NoSuchMethodException  If the main entry method cannot be found.
     * @throws NoSuchRealmException   If the main entry realm cannot be found.
     */
    protected Method getMainMethod()
        throws ClassNotFoundException, NoSuchMethodException, NoSuchRealmException
    {
        Method m = getMainClass().getMethod( "main", String[].class );

        int modifiers = m.getModifiers();

        if ( Modifier.isStatic( modifiers ) && Modifier.isPublic( modifiers ) )
        {
            if ( m.getReturnType() == Integer.TYPE || m.getReturnType() == Void.TYPE )
            {
                return m;
            }
        }

        throw new NoSuchMethodException( "public static void main(String[] args) in " + getMainClass() );
    }

    /**
     * Launch the application.
     *
     * @param args The application args.
     * @throws ClassNotFoundException    If the main entry class cannot be found.
     * @throws IllegalAccessException    If the method cannot be accessed.
     * @throws InvocationTargetException If the target of the invokation is invalid.
     * @throws NoSuchMethodException     If the main entry method cannot be found.
     * @throws NoSuchRealmException      If the main entry realm cannot be found.
     *
     * 启动配置文件中指定的应用
     */
    public void launch( String[] args )
        throws ClassNotFoundException, IllegalAccessException, InvocationTargetException, NoSuchMethodException,
        NoSuchRealmException
    {
        try
        {
            // 先尝试使用增强的main方法启动应用：public static void main(String[] args, ClassWorld world)
            launchEnhanced( args );

            return;
        }
        catch ( NoSuchMethodException e )
        {
            // ignore
        }

        // 如果增强的main方法启用不成功，则使用标准的main方法启动应用：public static void main(String[] args)
        launchStandard( args );
    }

    /**
     * <p>Attempt to launch the application through the enhanced main method.</p>
     * 
     * <p>This will seek a method with the exact signature of:</p>
     * <pre>
     *  public static void main(String[] args, ClassWorld world)
     *  </pre>
     *
     * @param args The application args.
     * @throws ClassNotFoundException    If the main entry class cannot be found.
     * @throws IllegalAccessException    If the method cannot be accessed.
     * @throws InvocationTargetException If the target of the invokation is
     *                                   invalid.
     * @throws NoSuchMethodException     If the main entry method cannot be found.
     * @throws NoSuchRealmException      If the main entry realm cannot be found.
     *
     * 使用增强的main方法启动应用：public static void main(String[] args, ClassWorld world)
     */
    protected void launchEnhanced( String[] args )
        throws ClassNotFoundException, IllegalAccessException, InvocationTargetException, NoSuchMethodException,
        NoSuchRealmException
    {
        // 获取main方法对应的Realm实例
        ClassRealm mainRealm = getMainRealm();

        // 使用ClassRealm加载main方法所在的类
        Class<?> mainClass = getMainClass();

        // 获取增强的main方法
        Method mainMethod = getEnhancedMainMethod();

        ClassLoader cl = mainRealm;

        // ----------------------------------------------------------------------
        // This is what the classloader for the main realm looks like when we
        // boot from the command line:
        // ----------------------------------------------------------------------
        // [ AppLauncher$AppClassLoader ] : $CLASSPATH envar
        //           ^
        //           |
        //           |
        // [ AppLauncher$ExtClassLoader ] : ${java.home}/jre/lib/ext/*.jar
        //           ^
        //           |
        //           |
        // [ Strategy ]
        // ----------------------------------------------------------------------

        Thread.currentThread().setContextClassLoader( cl );

        // 反射调用增强的main方法
        Object ret = mainMethod.invoke( mainClass, args, getWorld() );

        if ( ret instanceof Integer )
        {
            exitCode = (Integer) ret;
        }

        Thread.currentThread().setContextClassLoader( systemClassLoader );
    }

    /**
     * <p>Attempt to launch the application through the standard main method.</p>
     * 
     * <p>This will seek a method with the exact signature of:</p>

     * <pre>
     *  public static void main(String[] args)
     *  </pre>
     *
     * @param args The application args.
     * @throws ClassNotFoundException    If the main entry class cannot be found.
     * @throws IllegalAccessException    If the method cannot be accessed.
     * @throws InvocationTargetException If the target of the invokation is
     *                                   invalid.
     * @throws NoSuchMethodException     If the main entry method cannot be found.
     * @throws NoSuchRealmException      If the main entry realm cannot be found.
     *
     * 标准的main方法启动应用：public static void main(String[] args)
     */
    protected void launchStandard( String[] args )
        throws ClassNotFoundException, IllegalAccessException, InvocationTargetException, NoSuchMethodException,
        NoSuchRealmException
    {
        // 获取main方法对应的Realm实例
        ClassRealm mainRealm = getMainRealm();

        // 使用ClassRealm加载main方法所在的类
        Class<?> mainClass = getMainClass();

        // 获取main方法
        Method mainMethod = getMainMethod();

        Thread.currentThread().setContextClassLoader( mainRealm );

        // 使用反射调用main方法
        Object ret = mainMethod.invoke( mainClass, new Object[]{args} );

        if ( ret instanceof Integer )
        {
            exitCode = (Integer) ret;
        }

        Thread.currentThread().setContextClassLoader( systemClassLoader );

    }

    // ------------------------------------------------------------
    //     Class methods
    // ------------------------------------------------------------

    /**
     * Launch the launcher from the command line.
     * Will exit using System.exit with an exit code of 0 for success, 100 if there was an unknown exception,
     * or some other code for an application error.
     *
     * @param args The application command-line arguments.
     *
     * plexus-classworlds的启动类的main方法
     */
    public static void main( String[] args )
    {
        try
        {
            // 解析参数，并启动配置文件中指定的启动类
            int exitCode = mainWithExitCode( args );

            System.exit( exitCode );
        }
        catch ( Exception e )
        {
            e.printStackTrace();

            System.exit( 100 );
        }
    }

    /**
     * Launch the launcher.
     *
     * @param args The application command-line arguments.
     * @return an integer exit code
     * @throws Exception If an error occurs.
     *
     * 解析参数，并启动配置文件中指定的启动类
     */
    public static int mainWithExitCode( String[] args )
        throws Exception
    {
        // 从系统属性classworlds.conf中获取配置文件
        String classworldsConf = System.getProperty( CLASSWORLDS_CONF );

        InputStream is;

        Launcher launcher = new Launcher();

        // 类加载器
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // 设置系统类加载器
        launcher.setSystemClassLoader( cl );

        if ( classworldsConf != null )
        {
            // 读取配置文件
            is = Files.newInputStream( Paths.get( classworldsConf ) );
        }
        else
        {
            if ( "true".equals( System.getProperty( "classworlds.bootstrapped" ) ) )
            {
                is = cl.getResourceAsStream( UBERJAR_CONF_DIR + CLASSWORLDS_CONF );
            }
            else
            {
                is = cl.getResourceAsStream( CLASSWORLDS_CONF );
            }
        }

        if ( is == null )
        {
            throw new Exception( "classworlds configuration not specified nor found in the classpath" );
        }

        // 对配置文件中的内容进行解析并进行配置
        launcher.configure( is );

        is.close();

        try
        {
            // 启动配置文件中指定的应用
            launcher.launch( args );
        }
        catch ( InvocationTargetException e )
        {
            ClassRealm realm = launcher.getWorld().getRealm( launcher.getMainRealmName() );

            URL[] constituents = realm.getURLs();

            System.out.println( "---------------------------------------------------" );

            for ( int i = 0; i < constituents.length; i++ )
            {
                System.out.println( "constituent[" + i + "]: " + constituents[i] );
            }

            System.out.println( "---------------------------------------------------" );

            // Decode ITE (if we can)
            Throwable t = e.getTargetException();

            if ( t instanceof Exception )
            {
                throw (Exception) t;
            }
            if ( t instanceof Error )
            {
                throw (Error) t;
            }

            // Else just toss the ITE
            throw e;
        }

        return launcher.getExitCode();
    }
}
