# Distributed Programming Project

This repository contains the source code of a project developed as part of a distributed system that enables communication between servers and clients, user account management, file sharing, and asynchronous notifications.

## Environment Setup

Before running the project, you need to set up the development environment:

1. **Database:** Use MySQLWorkbench to create a database by executing the SQL code provided in the `database-code.sql` file.

2. **User Accounts:** Create user accounts with administrative privileges using MySQLWorkbench.

3. **Server and Clients:** Start one or more servers, providing the IP of the Database and the RMI IP. The server IPs and ports will be indicated during startup.

## Usage

After setting up the environment, follow the steps below to use the system:

1. **Server Initialization:** Start the server and provide the Database IP and Registry IP as needed.

2. **Client Initialization:** Start the client and provide the IP address and TCP port of the server you wish to connect to.

3. **Authentication:** Authenticate with an existing account or create a new account if necessary, following the instructions provided by the client.

4. **File Sharing:** Place the files you want to share in the indicated directory. The system will automatically detect the available files.

5. **Client Features:** Use the options provided by the client to send messages, transfer files, view history, and other available features.

## Architecture and Technologies Used

The system uses a distributed architecture, with TCP and UDP communications between servers and clients. The main technologies used include:

- **Java RMI:** For implementing remote services.
- **MySQL:** For storing system data.
- **NetBeans:** Development environment used for creating the project.

## Contributing

Contributions are welcome! If you'd like to contribute to the project, please open an issue to discuss the proposed changes or submit a pull request with your changes.

## License

This project is licensed under the [MIT License](LICENSE).
