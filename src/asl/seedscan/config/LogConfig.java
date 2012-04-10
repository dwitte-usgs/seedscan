/*
 * Copyright 2012, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/  >.
 *
 */
package asl.seedscan.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LogConfig
{
    private static final Logger logger = Logger.getLogger("asl.seedscan.config.LogConfig");

    private File directory = null;
    private String prefix = null;
    private String suffix = null;
    private Hashtable<String, Level> levels = null;

 // constructor(s)
    public LogConfig()
    {
        levels = new Hashtable<String, Level>();
    }

 // ready
    public boolean isReady()
    {
        return (directory == null) ? false :
               (prefix    == null) ? false :
               (suffix    == null) ? false : true;
    }

 // directory
    public void setDirectory(File directory)
    {
        this.directory = directory;
    }

    public void setDirectory(String directory)
    throws FileNotFoundException,
           IOException,
           NullPointerException,
           SecurityException
    {
        File path = new File(directory);
        if (!path.exists()) {
            throw new FileNotFoundException("Path '" +directory+ "' does not exist");
        }
        if (!path.isDirectory()) {
            throw new IOException("Path '" +directory+ "' is not a directory");
        }
        if (!path.canWrite()) {
            throw new SecurityException("Not permitted to write to directory '" +directory+ "'");
        }
        logger.config("Directory: "+directory);
        this.directory = path;
    }

    public File getDirectory()
    {
        return directory;
    }

 // prefix
    public void setPrefix(String prefix)
    {
        logger.config("Prefix: "+prefix);
        this.prefix = prefix;
    }

    public String getPrefix()
    {
        return prefix;
    }

 // suffix
    public void setSuffix(String suffix)
    {
        logger.config("Suffix: "+suffix);
        this.suffix = suffix;
    }

    public String getSuffix()
    {
        return suffix;
    }

 // levels
    public void setLevel(String name, String level)
    throws IllegalArgumentException
    {
        setLevel(name, Level.parse(level));
    }

    public void setLevel(String name, Level level)
    {
        logger.config("Level: '"+name+"' -> '"+level.toString());
        levels.put(name, level);
        Logger.getLogger(name).setLevel(level);
    }

    public Level getLevel(String name)
    {
        return levels.get(name);
    }

    public Enumeration<String> getLevelNames()
    {
        return levels.keys();
    }
}

