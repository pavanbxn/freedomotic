/**
 *
 * Copyright (c) 2009-2013 Freedomotic team
 * http://freedomotic.com
 *
 * This file is part of Freedomotic
 *
 * This Program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This Program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freedomotic; see the file COPYING.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package it.freedomotic.environment;

import it.freedomotic.app.Freedomotic;
import it.freedomotic.model.environment.Environment;
import it.freedomotic.model.environment.Zone;
import it.freedomotic.objects.EnvObjectLogic;
import it.freedomotic.objects.EnvObjectPersistence;
import it.freedomotic.persistence.FreedomXStream;
import it.freedomotic.security.Auth;
import it.freedomotic.util.DOMValidateDTD;
import it.freedomotic.util.Info;
import it.freedomotic.util.SerialClone;
import it.freedomotic.util.UidGenerator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import com.thoughtworks.xstream.XStream;

/**
 *
 * @author Enrico
 */
public class EnvironmentPersistence {

    private static List<EnvironmentLogic> envList = new ArrayList< EnvironmentLogic>();

    private EnvironmentPersistence() {
        //disable instance creation
    }
@RequiresPermissions("environments:save")
    public static void saveEnvironmentsToFolder(File folder) {
        if (envList.isEmpty()) {
            Freedomotic.logger.warning("There is no environment to persist, " + folder.getAbsolutePath() + " will not be altered.");
            return;
        }
        if (!folder.isDirectory()) {
            Freedomotic.logger.warning(folder.getAbsoluteFile() + " is not a valid environment folder. Skipped");
            return;
        }
        deleteEnvFiles(folder);
        try {
            // Create file
            StringBuilder summary = new StringBuilder();
            //print an header for the index.txt file
            summary.append("#Filename \t\t #EnvName").append("\n");
            for (EnvironmentLogic environment : envList) {
                String uuid = environment.getPojo().getUUID();
                if (uuid == null || uuid.isEmpty()) {
                    environment.getPojo().setUUID(UUID.randomUUID().toString());
                }
                String fileName = environment.getPojo().getUUID() + ".xenv";
                save(environment, new File(folder + "/" + fileName));
                summary.append(fileName).append("\t").append(environment.getPojo().getName()).append("\n");

            }
            //writing a summary .txt file with the list of commands in this folder
            FileWriter fstream = new FileWriter(folder + "/index.txt");
            BufferedWriter indexfile = new BufferedWriter(fstream);
            indexfile.write(summary.toString());
            //Close the output stream
            indexfile.close();
        } catch (Exception e) {
            Freedomotic.logger.info(e.getLocalizedMessage());
            Freedomotic.logger.severe(Freedomotic.getStackTraceInfo(e));
        }
    }

    @RequiresPermissions("environments:save")
    private static void deleteEnvFiles(File folder) {
        // This filter only returns object files
        FileFilter objectFileFileter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isFile() && file.getName().endsWith(".xenv")) {
                    return true;
                } else {
                    return false;
                }
            }
        };
        File[] files = folder.listFiles(objectFileFileter);
        for (File file : files) {
            file.delete();
        }
    }

    /**
     * Loads all objects file filesystem folder and adds the objects to the list
     *
     * @param folder
     * @param makeUnique
     */
    @RequiresPermissions("environments:load")
    public synchronized static boolean loadEnvironmentsFromDir(File folder, boolean makeUnique) {
        envList.clear();
        boolean check = true;
        // This filter only returns env files
        FileFilter envFileFilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isFile() && file.getName().endsWith(".xenv")) {
                    return true;
                } else {
                    return false;
                }
            }
        };
        File[] files = folder.listFiles(envFileFilter);
        for (File file : files) {
            try {
                loadEnvironmentFromFile(file);
            } catch (Exception e) {
                Freedomotic.logger.severe("Exception while loading environment in " + file.getAbsolutePath() + ".\n" + Freedomotic.getStackTraceInfo(e));
                check = false;
                break;
            }
        }
        if (check) {
            EnvObjectPersistence.loadObjects(EnvironmentPersistence.getEnvironments().get(0).getObjectFolder(), false);
        }
        return check;
    }

    /**
     * Add an environment. You can use EnvObjectPersistnce.MAKE_UNIQUE to create
     * an object that will surely be unique. Beware this means it is created
     * with defensive copy of the object in input and name, protocol, address
     * and UUID are reset to a default value.
     *
     * @param obj the environment to add
     * @param MAKE_UNIQUE can be true or false. Creates a defensive copy
     * reference to the object in input.
     * @return A pointer to the newly created environment object
     */
    @RequiresPermissions("environments:create")
    public static EnvironmentLogic add(final EnvironmentLogic obj, boolean MAKE_UNIQUE) {
        if (obj == null
                || obj.getPojo() == null
                || obj.getPojo().getName() == null
                || obj.getPojo().getName().isEmpty()) {
            throw new IllegalArgumentException("This is not a valid environment");
        }
        EnvironmentLogic envLogic = obj;
        if (MAKE_UNIQUE) {
            envLogic = new EnvironmentLogic();
            //defensive copy to not affect the passed object with the changes
            Environment pojoCopy = SerialClone.clone(obj.getPojo());
            pojoCopy.setName(obj.getPojo().getName() + "-" + UidGenerator.getNextStringUid());
            envLogic.setPojo(pojoCopy);
            envLogic.getPojo().setUUID("");
        }
        envLogic.init();
        //  if (!envList.contains(envLogic)) {
        envList.add(envLogic);
        //envLogic.setChanged(true);
        //  } else {
        //      throw new RuntimeException("Cannot add the same environment more than one time");
        //  }
        return envLogic;
    }
    
    @RequiresPermissions("environments:delete")
    public static void remove(EnvironmentLogic input) {
        for (EnvObjectLogic obj : EnvObjectPersistence.getObjectByEnvironment(input.getPojo().getUUID())) {
            EnvObjectPersistence.remove(obj);
        }
        envList.remove(input);
        input.clear();
    }
    
    @RequiresPermissions("environments:delete")
    public static void clear() {
        try {
            envList.clear();
        } catch (Exception e) {
        }
    }
    
    public static int size() {
        return envList.size();
    }
    @RequiresPermissions("environments:save")
    public static void save(EnvironmentLogic env, File file) throws IOException {
        XStream xstream = FreedomXStream.getEnviromentXstream();
        for (Zone zone : env.getPojo().getZones()) {
            zone.setObjects(null);
        }
        String xml = xstream.toXML(env.getPojo());
        FileWriter fstream;
        BufferedWriter out = null;
        try {
            Freedomotic.logger.info("Serializing environment to " + file);
            fstream = new FileWriter(file);
            out = new BufferedWriter(fstream);
            out.write(xml);
            //Close the output stream
            Freedomotic.logger.info("Application environment " + env.getPojo().getName() + " succesfully serialized");
        } catch (IOException ex) {
            Logger.getLogger(Environment.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            out.close();
        }
    }
    
    @RequiresPermissions("environments:save")
    public static void saveAs(EnvironmentLogic env, File folder) throws IOException {
        Freedomotic.logger.info("Serializing new environment to " + folder);
        String fileName = folder.getName();
        if (!folder.exists()) {
            folder.mkdir();
            new File(folder + "/data").mkdir();
            new File(folder + "/data/obj").mkdir();
            new File(folder + "/data/rea").mkdir();
            new File(folder + "/data/trg").mkdir();
            new File(folder + "/data/cmd").mkdir();
            new File(folder + "/data/resources").mkdir();
        }
        save(env, new File(folder + "/" + fileName + ".xenv"));
        //TODO: Freedomotic.environment.getPojo().setObjectsFolder()
        //  EnvObjectPersistence.saveObjects(new File(folder + "/objects"));
    }
    
    @RequiresPermissions("environments:load")
    public static void loadEnvironmentFromFile(final File file) throws IOException {
        XStream xstream = FreedomXStream.getXstream();
        //validate the object against a predefined DTD
        String xml = DOMValidateDTD.validate(file, Info.getApplicationPath() + "/config/validator/environment.dtd");
        Environment pojo = null;
        try {
            pojo = (Environment) xstream.fromXML(xml);
        } catch (Exception e) {
            Freedomotic.logger.severe("XML parsing error. Readed XML is \n" + xml);

        }
        EnvironmentLogic envLogic = new EnvironmentLogic();
        envLogic.setPojo(pojo);
        envLogic.setSource(file);
        // next line is commented as the method init() is called in the add()
        //envLogic.init();
        add(envLogic, false);

    }
@RequiresPermissions("environments:read")
    public static List<EnvironmentLogic> getEnvironments() {
        return envList;
    }
    
    @RequiresPermissions("environments:read")
    public static EnvironmentLogic getEnvByUUID(String UUID) {
        if (Auth.isPermitted("environments:read:"+UUID)) {
        for (EnvironmentLogic env : envList) {
            if (env.getPojo().getUUID().equals(UUID)) {
                return env;
            }
        }
        }
        return null;
    }
}