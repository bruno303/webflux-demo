CREATE TABLE PERSON (
    ID UUID PRIMARY KEY,
    NAME VARCHAR(300) NOT NULL,
    BIRTH_DATE DATE NOT NULL,
    EXTERNAL_ID VARCHAR(300) NULL
);