/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.usc.irds.sparkler.plugin;


import edu.usc.irds.sparkler.JobContext;
import edu.usc.irds.sparkler.SparklerConfiguration;
import edu.usc.irds.sparkler.SparklerException;
import edu.usc.irds.sparkler.model.FetchedData;
import edu.usc.irds.sparkler.model.Resource;
import edu.usc.irds.sparkler.model.ResourceStatus;
import edu.usc.irds.sparkler.util.FetcherDefault;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedHashMap;

@Extension
public class FetcherChrome extends FetcherDefault {

    private static final Integer DEFAULT_TIMEOUT = 2000;
    private static final Logger LOG = LoggerFactory.getLogger(FetcherChrome.class);
    private LinkedHashMap<String, Object> pluginConfig;
    private WebDriver driver;


    @Override
    public void init(JobContext context, String pluginId) throws SparklerException {
        super.init(context, pluginId);

        SparklerConfiguration config = jobContext.getConfiguration();
        //TODO should change everywhere 
        pluginConfig = config.getPluginConfiguration(pluginId);
        String loc = (String) pluginConfig.getOrDefault("chrome.dns", "");
        if(loc.equals("")){
            driver = new ChromeDriver();
        }
        else{
            try {
                final DesiredCapabilities desiredCapabilities = DesiredCapabilities.chrome();
                final ChromeOptions chromeOptions = new ChromeOptions();
                chromeOptions.addArguments("--no-sandbox");
                chromeOptions.addArguments("--headless");
                desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);

                driver = new RemoteWebDriver(new URL(loc), desiredCapabilities);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

        }

    }

    @Override
    public FetchedData fetch(Resource resource) throws Exception {
        LOG.info("Chrome FETCHER {}", resource.getUrl());
        FetchedData fetchedData;
        /*
		* In this plugin we will work on only HTML data
		* If data is of any other data type like image, pdf etc plugin will return client error
		* so it can be fetched using default Fetcher
		*/
        if (!isWebPage(resource.getUrl())) {
            LOG.debug("{} not a html. Falling back to default fetcher.", resource.getUrl());
            //This should be true for all URLS ending with 4 character file extension
            //return new FetchedData("".getBytes(), "application/html", ERROR_CODE) ;
            return super.fetch(resource);
        }
        long start = System.currentTimeMillis();

        LOG.debug("Time taken to create driver- {}", (System.currentTimeMillis() - start));

        // This will block for the page load and any
        // associated AJAX requests
        driver.get(resource.getUrl());

        URL url = new URL(resource.getUrl());
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        int status = connection.getResponseCode();
        //content-type

        // Returns the page source in its current state, including
        // any DOM updates that occurred after page load
        String html = driver.getPageSource();

        //quitBrowserInstance(driver);

        LOG.debug("Time taken to load {} - {} ", resource.getUrl(), (System.currentTimeMillis() - start));

        if (!(status >= 200 && status < 300)) {
            // If not fetched through plugin successfully
            // Falling back to default fetcher
            LOG.info("{} Failed to fetch the page. Falling back to default fetcher.", resource.getUrl());
            return super.fetch(resource);
        }
        fetchedData = new FetchedData(html.getBytes(), "application/html", status);
        resource.setStatus(ResourceStatus.FETCHED.toString());
        fetchedData.setResource(resource);
        return fetchedData;
    }

    public void closeResources() {
        quitBrowserInstance(driver);
    }

    private boolean isWebPage(String webUrl) {
        try {
            URLConnection conn = new URL(webUrl).openConnection();
            String contentType = conn.getHeaderField("Content-Type");
            return contentType.contains("text") || contentType.contains("ml");
        } catch (Exception e) {
            LOG.debug(e.getMessage(), e);
        }
        return false;
    }

    private boolean hasDriverQuit() {
        try {
            String result = driver.toString();
            return result.contains("(null)");
        } catch (Exception e) {
            LOG.debug(e.getMessage(), e);
        }
        return false;
    }

    public void quitBrowserInstance(WebDriver driver) {
        if (driver != null) {
            if (!hasDriverQuit()) {
                try {
                    // FIXME - Exception when closing the driver. Adding an unused GET request
                    driver.get("http://www.apache.org/");
                    driver.quit();
                } catch (Exception e) {
                    LOG.debug("Exception {} raised. The driver is either already closed " +
                            "or this is an unknown exception", e.getMessage());
                }
            } else {
                LOG.debug("Driver is already quit");
            }
        } else {
            LOG.debug("Driver was null");
        }
    }

}
