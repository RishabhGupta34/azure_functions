// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.resourcemanager.appservice.samples;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appservice.models.AppServicePlan;
import com.azure.resourcemanager.appservice.models.FunctionApp;
import com.azure.resourcemanager.appservice.models.FunctionRuntimeStack;
import com.azure.resourcemanager.appservice.models.PublishingProfile;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.resources.fluentcore.utils.ResourceManagerUtils;
import com.azure.resourcemanager.samples.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * Azure App Service basic sample for managing function apps.
 *    - Create a function app with a new app service plan
 *    - Create a 2nd function app with zip deploy
 *    - Deploy to 3rd function app through ZIP deploy
 *    - Deploy to 4th function app with zip deploy, which is already created
 *    - Change runtime to python for 5th Function App
 *    - Deploy to the 6th function app through docker container deploy
 */
public final class ManageFunctionAppSourceControl {

    /**
     * Main function which runs the actual sample.
     * @param azureResourceManager instance of the azure client
     * @return true if sample runs successfully
     */
    public static boolean runSample(AzureResourceManager azureResourceManager) throws GitAPIException {
        // New resources
        final String suffix         = ".azurewebsites.net";
        final String app1Name       = Utils.randomResourceName(azureResourceManager, "webapp1-", 20);
        final String app2Name       = Utils.randomResourceName(azureResourceManager, "webapp2-", 20);
        final String app2Url        = app2Name + suffix;
        final String rgName         = Utils.randomResourceName(azureResourceManager, "rg1NEMV_", 24);

        try {
            //============================================================
            // Create a function app with a new app service plan

            System.out.println("Creating function app " + app1Name + " in resource group " + rgName + "...");

            FunctionApp app1 = azureResourceManager.functionApps().define(app1Name)
                    .withRegion(Region.US_WEST)
                    .withNewResourceGroup(rgName)
                    .create();

            System.out.println("Created function app " + app1.name());
            Utils.print(app1);

            AppServicePlan plan = azureResourceManager.appServicePlans().getById(app1.appServicePlanId());

            //============================================================
            // Create a 2nd function app with zip deploy

            System.out.println("Creating another function app " + app2Name + "...");
            FunctionApp app2 = azureResourceManager.functionApps()
                    .define(app2Name)
                    .withExistingAppServicePlan(plan)
                    .withExistingResourceGroup(rgName)
                    .create();

            System.out.println("Created function app " + app2.name());

            //============================================================
            // Deploy to 3rd function app through ZIP deploy

            System.out.println("Deploying nodejs-function-app.zip to " + app2Name + " through ZIP deploy...");

            Mono deployment1 = app2.zipDeployAsync(new File(ManageFunctionAppSourceControl.class.getResource("/nodejs-function-app.zip").getPath()));
            deployment1.block(Duration.ofMinutes(5));
            // warm up
            System.out.println("Warming up " + app2Url + "/api/square...");
            Utils.sendPostRequest("http://" + app2Url + "/api/square", "926");
            ResourceManagerUtils.sleep(Duration.ofSeconds(5));
            System.out.println("CURLing " + app2Url + "/api/square...");
            System.out.println("Square of 926 is " + Utils.sendPostRequest("http://" + app2Url + "/api/square", "926"));

            //============================================================
            // Deploy to 4th function app with zip deploy, which is already created

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            LocalDateTime now;

            FunctionApp app3 = azureResourceManager.functionApps().getByResourceGroup("rishabh-python", "rishabh-python");
            now = LocalDateTime.now();
            System.out.println("Started async Deploy python app " + app1 + " through ZIP deploy... at: " + dtf.format(now));
            Mono deployment2 = app3.zipDeployAsync(new File(ManageFunctionAppSourceControl.class.getResource("/python-function-app.zip").getPath()));
            deployment2.block(Duration.ofMinutes(5));
            now = LocalDateTime.now();
            System.out.println("Deployed python-function-app.zip to app " + app3.name() + " at: "+ dtf.format(now));

            ResourceManagerUtils.sleep(Duration.ofSeconds(25));

            //============================================================
            // Change runtime to python for 5th Function App

            FunctionApp app4 = azureResourceManager.functionApps().getByResourceGroup("rishabh-node", "rishabh-node");
            now = LocalDateTime.now();
            System.out.println("Started change runtime to python for " + app4.name() + " at: " + dtf.format(now));

            Mono deployment3 = app4.update().withBuiltInImage(new FunctionRuntimeStack("python","~4", "python|3.10")).applyAsync();
            deployment3.block(Duration.ofMinutes(5));
            now = LocalDateTime.now();
            System.out.println("Changed runtime to python for " + app4.name() + " at: " + dtf.format(now));

            //============================================================
            // Deploy to the 6th function app through docker container deploy

            FunctionApp app5 = azureResourceManager.functionApps().getByResourceGroup("rishabh-docker", "rishabh-docker");
            now = LocalDateTime.now();
            System.out.println("Started async Deploy docker container app " + app5.name() + " through docker deploy..." + dtf.format(now));
            Mono deployment4 = app5.update().withPublicDockerHubImage("rishgupta34/azure_artifact:latest").applyAsync();
            deployment4.block(Duration.ofMinutes(5));
            now = LocalDateTime.now();
            System.out.println("Deployed docker container app " + app5.name() + " at: "+ dtf.format(now));
            return true;

        } finally {
            try {
                System.out.println("Done");
            } catch (NullPointerException npe) {
                System.out.println("Did not create any resources in Azure. No clean up is necessary");
            } catch (Exception g) {
                g.printStackTrace();
            }
        }
    }
    /**
     * Main entry point.
     * @param args the parameters
     */
    public static void main(String[] args) {
        try {

            //=============================================================
            // Authenticate

            final AzureProfile profile = new AzureProfile(AzureEnvironment.AZURE);
            final TokenCredential credential = new DefaultAzureCredentialBuilder()
                .authorityHost(profile.getEnvironment().getActiveDirectoryEndpoint())
                .build();

            AzureResourceManager azureResourceManager = AzureResourceManager
                .configure()
                .withLogLevel(HttpLogDetailLevel.BASIC)
                .authenticate(credential, profile)
                .withDefaultSubscription();

            // Print selected subscription
            System.out.println("Selected subscription: " + azureResourceManager.subscriptionId());

            runSample(azureResourceManager);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
