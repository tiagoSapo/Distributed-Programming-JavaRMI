create database trabalho_pratico;
create table Utilizadores (
utilizador_id int not null auto_increment primary key unique, 
username varchar(30) not null, /* nome da conta do utilizador */
nome varchar(30) not null, /* apenas o nome do utilizador */
palavra_passe varchar(30) not null, 
autenticado boolean not null, 
ip varchar(30),  /* ip do programa cliente do utilizador */
udp int, /* username do utilizador */
tcp_clientes int, /* porto tcp que agurda ligacoes de outros clientes para efetuar a transferencia de FICHEIROS */
tcp_servidor int, /* porto tcp responsavel pela invocacao de metodos por parte do cliente ao servidor */
tcp_notificacoes_servidor int, /* porto tcp responsavel pela recepcao de notificacoes do servidor */
falhas int not null /* contador numero de falhas desta conta (e colocado a 0 sempre que o cliente se desautentique) (Maximo 3 falhas)*/);

create table Ficheiros (
ficheiro_id int not null auto_increment primary key unique, 
nome varchar(30) not null /* nome do ficheiro disponibilizado */,
utilizador_id int not null,
FOREIGN KEY (utilizador_id) REFERENCES Utilizadores(utilizador_id) on delete cascade
);

create table Transferencias (
transferencia_id int not null auto_increment primary key unique, 
username_origem varchar(30) not null, /* username do cliente que disponibiliza o ficheiro */
username_destino varchar(30) not null, /* username do cliente que recebe uma copia do ficheiro */
ficheiro varchar(30) not null,  /*  nome do ficheiro em causa */
tipo varchar(30) not null, /* download ou upload */
ocorrencia Timestamp not null, /* data e hora da ocorrencia */
utilizador_id int not null,
FOREIGN KEY (utilizador_id) REFERENCES Utilizadores(utilizador_id) on delete cascade
);
