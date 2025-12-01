# How to Rebuild and Restart the Application with Fresh Code

## The Problem: Why Your Code Changes Don't Appear

You've noticed that even after changing the Java source code, the running application doesn't reflect these changes. This happens because Docker doesn't automatically rebuild your application's image every time you restart a container.

The old `cleanup.sh` script would stop and restart the `app` container but would reuse the *existing*, stale Docker image (`flyingcloud-4156:latest`). To include your latest code, you must explicitly tell Docker to **rebuild** the image from your source code.

## The Solution: Forcing a Rebuild

There are two primary ways to ensure your environment is always running the latest code: using the provided `Makefile` or using `docker compose` commands directly. The `scripts/cleanup.sh` script has also been updated to perform these steps correctly.

### Method 1: Using the `cleanup.sh` Script (Recommended)

The simplest way to reset everything is to run the updated `cleanup.sh` script. This script now handles the entire process of tearing down the old environment and building a new one.

```bash
# Make sure the script is executable
chmod +x ./scripts/cleanup.sh

# Run the script from the project root
./scripts/cleanup.sh
```

This script will:
1.  Stop and remove all existing containers, networks, and volumes (`docker compose down -v`).
2.  Rebuild the `app` image using the latest source code (`docker compose up --build`).
3.  Start all services (`mysql`, `redis`, `app`) in the correct order.

### Method 2: Using the `Makefile`

The project's `Makefile` provides convenient shortcuts for common operations.

1.  **Full Clean and Rebuild:**
    This is the most thorough option. It stops and removes everything, then builds and starts the services.

    ```bash
    # From the project root directory
    make clean
    make up
    ```
    *   `make clean`: Stops containers, removes them, and also deletes the local data directories for MySQL and Redis.
    *   `make up`: Builds the `app` image and starts all services.

2.  **Simple Rebuild and Restart:**
    If you just want to update the code without deleting the database content, you can use:

    ```bash
    # This will stop, remove containers, rebuild the app, and start everything.
    # The database data will persist if you don't run 'make clean'.
    make down
    make up
    ```

### Method 3: Using `docker compose` Commands Manually

If you prefer to run the commands yourself, here is the sequence:

1.  **Stop and Remove Existing Environment:**
    The `-v` flag is important as it removes the volumes where MySQL data is stored, ensuring a completely fresh start.

    ```bash
    # From the project root, using the correct env file
    docker compose --env-file .env-flyingcloud -f docker-compose.yml down -v
    ```

2.  **Build and Start the New Environment:**
    The `--build` flag is the key. It tells `docker compose` to look for services with a `build` configuration (like our `app` service) and rebuild their images before starting the containers.

    ```bash
    # From the project root
    docker compose --env-file .env-flyingcloud -f docker-compose.yml up --build -d
    ```
    *   `--build`: Rebuilds the `app` image.
    *   `-d`: Runs the containers in detached mode (in the background).

By following any of these methods, you can be confident that your Docker environment is running the very latest version of your code.
