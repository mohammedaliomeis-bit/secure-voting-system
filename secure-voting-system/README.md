# Secure Voting System

A secure web-based voting platform developed using **Java Spring Boot** that enables users to participate in elections through a reliable, transparent, and secure environment. The system integrates **blockchain technology** to store votes in an immutable ledger, ensuring vote integrity and protecting election results from tampering.

---

# Overview

The Secure Voting System is a full-stack web application designed to modernize the election process by providing a secure and efficient online voting platform. The application enables administrators to create and manage elections, register candidates, monitor voting activities, and oversee the entire election process. Authenticated voters can securely access the system, participate in available elections, and cast their votes through an intuitive interface.

To strengthen trust and ensure the integrity of election results, every submitted vote is recorded using blockchain technology. Each vote becomes part of an immutable blockchain, making it resistant to unauthorized modification or deletion after submission. By combining secure authentication, database management, and blockchain-based vote storage, the system demonstrates how modern technologies can be used to build trustworthy electronic voting applications.

---

# System Description

The Secure Voting System provides a complete solution for conducting digital elections. The platform manages users, elections, candidates, and voting records while maintaining high security standards throughout the voting process.

The system allows administrators to:

- Create and manage elections
- Add and manage candidates
- Monitor election activities
- Manage users and voting records

Authenticated voters can:

- Log into the system securely
- View available elections
- Cast a single secure vote
- Verify that their vote has been successfully recorded

Once a vote is submitted, it is permanently stored in the blockchain, ensuring transparency, integrity, and protection against vote manipulation.

---

# Features

- Secure user authentication and authorization
- Voter registration and login
- Election management
- Candidate management
- Secure vote submission
- **Blockchain-based vote storage**
- Tamper-resistant voting records
- Administrative dashboard
- File and document management
- System activity logging

---

# Blockchain Integration

Blockchain technology is one of the core components of this project.

Instead of storing votes only in a traditional database, every submitted vote is recorded as a block within a blockchain. Each block is cryptographically linked to the previous one using hash functions, creating an immutable chain of voting records.

This approach provides several important security advantages:

- Votes cannot be modified after submission.
- Every vote is permanently recorded.
- The integrity of election results can be verified.
- Any attempt to tamper with stored votes becomes immediately detectable.
- The voting process becomes more transparent and trustworthy.

Using blockchain technology significantly improves the reliability and security of electronic voting systems.

---

# Technologies Used

## Backend

- Java
- Spring Boot
- Spring MVC
- Spring Security
- Maven

## Frontend

- HTML
- CSS
- JavaScript
- Thymeleaf

## Database

- MySQL

## Additional Technologies

- Blockchain implementation for secure vote storage
- Git
- GitHub
- Logging
- File management

---

# Project Structure

```
secure-voting-system/
│
├── src/
├── uploads/
├── documents/
├── blockchain-data/
├── logs/
├── target/
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

---

# Installation

## Prerequisites

- Java JDK 17 (or the version required by the project)
- Maven
- MySQL
- Git

## Clone the Repository

```bash
git clone https://github.com/your-username/secure-voting-system.git
```

## Navigate to the Project

```bash
cd secure-voting-system
```

## Configure the Database

Update your database configuration inside:

```
src/main/resources/application.properties
```

## Build the Project

```bash
mvn clean install
```

## Run the Application

```bash
mvn spring-boot:run
```

The application will be available at:

```
http://localhost:8080
```

---

# Security Features

The application incorporates several security mechanisms, including:

- Secure user authentication
- Role-based authorization
- Protected vote submission
- Blockchain-backed vote storage
- Immutable voting records
- Secure database integration

---

# Future Improvements

Potential enhancements include:

- Two-Factor Authentication (2FA)
- Email verification
- Real-time election statistics
- Distributed blockchain network
- Smart contract integration
- Mobile application support
- Enhanced reporting and analytics dashboard

---

# Learning Outcomes

This project demonstrates practical knowledge and experience in:

- Full-stack web application development
- Spring Boot framework
- Database design and integration
- User authentication and authorization
- Blockchain concepts and implementation
- Secure software engineering
- Version control using Git and GitHub

---

# Author

**Mohammad Ali Omeis**

---
# License

This project is licensed under the MIT License. See the `LICENSE` file for more details.
