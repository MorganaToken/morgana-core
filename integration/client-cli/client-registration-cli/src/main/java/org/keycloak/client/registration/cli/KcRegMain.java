package org.keycloak.client.registration.cli;

import org.jboss.aesh.console.AeshConsoleBuilder;
import org.jboss.aesh.console.AeshConsoleImpl;
import org.jboss.aesh.console.Prompt;
import org.jboss.aesh.console.command.registry.AeshCommandRegistryBuilder;
import org.jboss.aesh.console.command.registry.CommandRegistry;
import org.jboss.aesh.console.settings.Settings;
import org.jboss.aesh.console.settings.SettingsBuilder;
import org.keycloak.client.registration.cli.aesh.AeshEnhancer;
import org.keycloak.client.registration.cli.aesh.ValveInputStream;
import org.keycloak.client.registration.cli.aesh.Globals;
import org.keycloak.client.registration.cli.commands.KcRegCmd;
import org.keycloak.client.registration.cli.util.ClassLoaderUtil;
import org.keycloak.common.crypto.CryptoIntegration;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author <a href="mailto:mstrukel@redhat.com">Marko Strukelj</a>
 */
public class KcRegMain {

    public static void main(String [] args) {
        String libDir = System.getProperty("kc.lib.dir");
        if (libDir == null) {
            throw new RuntimeException("System property kc.lib.dir needs to be set");
        }
        ClassLoader cl = ClassLoaderUtil.resolveClassLoader(libDir);
        Thread.currentThread().setContextClassLoader(cl);

        CryptoIntegration.init(cl);

        Globals.stdin = new ValveInputStream();

        Settings settings = new SettingsBuilder()
                .logging(false)
                .readInputrc(false)
                .disableCompletion(true)
                .disableHistory(true)
                .enableAlias(false)
                .enableExport(false)
                .inputStream(Globals.stdin)
                .create();

        CommandRegistry registry = new AeshCommandRegistryBuilder()
                .command(KcRegCmd.class)
                .create();

        AeshConsoleImpl console = (AeshConsoleImpl) new AeshConsoleBuilder()
                .settings(settings)
                .commandRegistry(registry)
                .prompt(new Prompt(""))
                .create();

        AeshEnhancer.enhance(console);

        // work around parser issues with quotes and brackets
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add("kcreg");
        arguments.addAll(Arrays.asList(args));
        Globals.args = arguments;

        StringBuilder b = new StringBuilder();
        for (String s : args) {
            // quote if necessary
            boolean needQuote = false;
            needQuote = s.indexOf(' ') != -1 || s.indexOf('\"') != -1 || s.indexOf('\'') != -1;
            b.append(' ');
            if (needQuote) {
                b.append('\'');
            }
            b.append(s);
            if (needQuote) {
                b.append('\'');
            }
        }
        console.setEcho(false);

        console.execute("kcreg" + b.toString());

        console.start();
    }
}
