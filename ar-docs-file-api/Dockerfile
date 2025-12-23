FROM ubuntu:latest

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update

# Install packages required to run the program.
RUN apt-get install -y build-essential libglu1-mesa libxi6 libgconf-2-4
RUN apt-get purge nodejs npm && apt-get install -y nodejs npm
RUN apt-get install -y python3-pip poppler-utils
RUN apt-get install -y software-properties-common vim curl
RUN apt-get update

# Copy and install requirements
COPY /app/requirements.txt .
RUN pip3 install -r requirements.txt

# Copy code
COPY /app /app

# Set target directory as working directory.
WORKDIR /app

# Expose ports.
EXPOSE 80

# Startup server with Gunicorn binded to the specified host:port.
CMD [ "gunicorn", "-c", "gunicorn_config.py", "wsgi:app" ]
